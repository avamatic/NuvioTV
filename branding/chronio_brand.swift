// Renders Chronio brand assets with CoreGraphics.
//
//   swift chronio_brand.swift <kind> <palette> <width> <height> <out.png>
//
// kind:    icon (full-bleed launcher), appicon (macOS rounded square), mark (transparent),
//          banner (Android TV), wordmark (mark + text, transparent), text (text only)
// palette: original, arctic_blue, emerald, rose_gold, copper, graphite
import AppKit
import CoreGraphics

let args = CommandLine.arguments
guard args.count == 6, let width = Int(args[3]), let height = Int(args[4]) else {
    FileHandle.standardError.write("usage: chronio_brand.swift <kind> <palette> <width> <height> <out.png>\n".data(using: .utf8)!)
    exit(2)
}
let kind = args[1], paletteName = args[2], outPath = args[5]

func rgb(_ hex: UInt32) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
}

// Ring gradient stops (start of sweep -> arrowhead).
let palettes: [String: [UInt32]] = [
    "original": [0xFFB23F, 0xFF5E62, 0xC04BFF],
    "arctic_blue": [0x7FF3FF, 0x3AA8FF, 0x4F5BFF],
    "emerald": [0xC8F560, 0x2FD68A, 0x0FA3A3],
    "rose_gold": [0xFFD6C2, 0xF29C9C, 0xD9668F],
    "copper": [0xFFC27A, 0xE07B39, 0xA8481F],
    "graphite": [0xF2F4F7, 0xA9B0BC, 0x6B7280],
    "gold": [0xFFE69A, 0xF5B83D, 0xCC8419],
    "jade": [0xC8F560, 0x2FD68A, 0x0FA3A3],
]
guard let stops = palettes[paletteName] else { FileHandle.standardError.write("unknown palette\n".data(using: .utf8)!); exit(2) }
let ringGradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: stops.map(rgb) as CFArray, locations: [0, 0.55, 1])!
let backgroundTop = rgb(0x121626), backgroundBottom = rgb(0x1E1233)

let space = CGColorSpaceCreateDeviceRGB()
guard let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { exit(1) }
let W = CGFloat(width), H = CGFloat(height)
ctx.setAllowsAntialiasing(true)
ctx.setShouldAntialias(true)
ctx.interpolationQuality = .high

func fillBackground(_ rect: CGRect, cornerRadius: CGFloat = 0) {
    ctx.saveGState()
    let path = CGPath(roundedRect: rect, cornerWidth: cornerRadius, cornerHeight: cornerRadius, transform: nil)
    ctx.addPath(path)
    ctx.clip()
    let gradient = CGGradient(colorsSpace: space, colors: [backgroundTop, backgroundBottom] as CFArray, locations: [0, 1])!
    ctx.drawLinearGradient(gradient, start: CGPoint(x: rect.midX, y: rect.maxY), end: CGPoint(x: rect.midX, y: rect.minY), options: [])
    ctx.restoreGState()
}

/// Clock ring sweeping clockwise into an arrowhead, around a play triangle. `rect` is square.
func drawMark(in rect: CGRect) {
    let s = rect.width
    let c = CGPoint(x: rect.midX, y: rect.midY)
    let r = s * 0.36, lineWidth = s * 0.115
    let start = 48.0 * Double.pi / 180, end = 128.0 * Double.pi / 180 // clockwise from start to end (280° sweep)

    let ring = CGMutablePath()
    ring.addArc(center: c, radius: r, startAngle: start, endAngle: end, clockwise: true)
    let stroked = ring.copy(strokingWithWidth: lineWidth, lineCap: .round, lineJoin: .round, miterLimit: 10)

    // Arrowhead at the end of the sweep, pointing along the clockwise tangent.
    let p = CGPoint(x: c.x + r * cos(end), y: c.y + r * sin(end))
    let dir = CGPoint(x: sin(end), y: -cos(end))
    let normal = CGPoint(x: cos(end), y: sin(end))
    let tipLength = s * 0.17, halfBase = s * 0.14
    let arrow = CGMutablePath()
    arrow.move(to: CGPoint(x: p.x + dir.x * tipLength, y: p.y + dir.y * tipLength))
    arrow.addLine(to: CGPoint(x: p.x + normal.x * halfBase - dir.x * s * 0.01, y: p.y + normal.y * halfBase - dir.y * s * 0.01))
    arrow.addLine(to: CGPoint(x: p.x - normal.x * halfBase - dir.x * s * 0.01, y: p.y - normal.y * halfBase - dir.y * s * 0.01))
    arrow.closeSubpath()

    // Paint ring and arrowhead separately; their stroked outlines wind in opposite directions.
    for shape in [stroked, arrow as CGPath] {
        ctx.saveGState()
        ctx.addPath(shape)
        ctx.clip()
        ctx.drawLinearGradient(ringGradient, start: CGPoint(x: rect.minX, y: rect.minY), end: CGPoint(x: rect.maxX, y: rect.maxY), options: [])
        ctx.restoreGState()
    }

    // Play triangle, nudged right for optical centering, with softened corners.
    let t = s * 0.30
    let play = CGMutablePath()
    let left = c.x - t * 0.36, right = c.x + t * 0.58
    play.move(to: CGPoint(x: left, y: c.y + t / 2))
    play.addLine(to: CGPoint(x: right, y: c.y))
    play.addLine(to: CGPoint(x: left, y: c.y - t / 2))
    play.closeSubpath()
    ctx.saveGState()
    ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
    ctx.setStrokeColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
    ctx.setLineWidth(s * 0.035)
    ctx.setLineJoin(.round)
    ctx.addPath(play)
    ctx.drawPath(using: .fillStroke)
    ctx.restoreGState()
}

func drawText(_ text: String, capHeightTarget: CGFloat, originX: CGFloat, baselineCenterY: CGFloat) -> CGFloat {
    let base = NSFont.systemFont(ofSize: 100, weight: .heavy)
    let descriptor = base.fontDescriptor.withDesign(.rounded) ?? base.fontDescriptor
    let probe = NSFont(descriptor: descriptor, size: 100) ?? base
    let size = 100 * capHeightTarget / probe.xHeight
    let font = NSFont(descriptor: descriptor, size: size) ?? base
    let attributed = NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: NSColor.white, .kern: size * 0.01])
    let line = CTLineCreateWithAttributedString(attributed)
    let width = CTLineGetTypographicBounds(line, nil, nil, nil)
    ctx.textPosition = CGPoint(x: originX, y: baselineCenterY - font.xHeight / 2)
    CTLineDraw(line, ctx)
    return CGFloat(width)
}

func textWidth(_ text: String, xHeight: CGFloat) -> CGFloat {
    let base = NSFont.systemFont(ofSize: 100, weight: .heavy)
    let descriptor = base.fontDescriptor.withDesign(.rounded) ?? base.fontDescriptor
    let probe = NSFont(descriptor: descriptor, size: 100) ?? base
    let size = 100 * xHeight / probe.xHeight
    let font = NSFont(descriptor: descriptor, size: size) ?? base
    let attributed = NSAttributedString(string: text, attributes: [.font: font, .kern: size * 0.01])
    return CGFloat(CTLineGetTypographicBounds(CTLineCreateWithAttributedString(attributed), nil, nil, nil))
}

let name = "chronio"
switch kind {
case "icon":
    fillBackground(CGRect(x: 0, y: 0, width: W, height: H))
    let side = min(W, H) * 0.64
    drawMark(in: CGRect(x: (W - side) / 2, y: (H - side) / 2, width: side, height: side))
case "appicon":
    // macOS grid: 824/1024 body with ~185/1024 corner radius.
    let body = min(W, H) * 824 / 1024
    let rect = CGRect(x: (W - body) / 2, y: (H - body) / 2, width: body, height: body)
    fillBackground(rect, cornerRadius: body * 0.225)
    let side = body * 0.66
    drawMark(in: CGRect(x: rect.midX - side / 2, y: rect.midY - side / 2, width: side, height: side))
case "mark":
    let side = min(W, H)
    drawMark(in: CGRect(x: (W - side) / 2, y: (H - side) / 2, width: side, height: side))
case "banner":
    fillBackground(CGRect(x: 0, y: 0, width: W, height: H))
    let side = H * 0.46, xHeight = H * 0.15, gap = H * 0.08
    let total = side + gap + textWidth(name, xHeight: xHeight)
    let x0 = (W - total) / 2
    drawMark(in: CGRect(x: x0, y: (H - side) / 2, width: side, height: side))
    _ = drawText(name, capHeightTarget: xHeight, originX: x0 + side + gap, baselineCenterY: H / 2)
case "wordmark":
    let side = H, xHeight = H * 0.34, gap = H * 0.14
    drawMark(in: CGRect(x: 0, y: 0, width: side, height: side))
    let available = W - side - gap
    let fitted = min(xHeight, xHeight * available / textWidth(name, xHeight: xHeight))
    _ = drawText(name, capHeightTarget: fitted, originX: side + gap, baselineCenterY: H / 2)
case "text":
    let xHeight = H * 0.62
    let fitted = min(xHeight, xHeight * W * 0.98 / textWidth(name, xHeight: xHeight))
    let width = textWidth(name, xHeight: fitted)
    _ = drawText(name, capHeightTarget: fitted, originX: (W - width) / 2, baselineCenterY: H / 2)
default:
    FileHandle.standardError.write("unknown kind\n".data(using: .utf8)!)
    exit(2)
}

guard let image = ctx.makeImage() else { exit(1) }
let rep = NSBitmapImageRep(cgImage: image)
guard let data = rep.representation(using: .png, properties: [:]) else { exit(1) }
try data.write(to: URL(fileURLWithPath: outPath))

import Foundation

/// Giá trị JSON giữ nguyên thứ tự khoá (giống org.json trên Android),
/// để việc "tìm đối tượng đầu tiên có khoá X" luôn cho cùng một kết quả.
public enum JSON {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([JSON])
    case object(JSONObject)

    /// Đọc JSON từ dữ liệu thô. Ném lỗi nếu không hợp lệ.
    public static func parse(_ data: Data) throws -> JSON {
        var parser = JSONByteParser(bytes: [UInt8](data))
        return try parser.parseDocument()
    }

    public static func parse(_ text: String) throws -> JSON {
        try parse(Data(text.utf8))
    }
}

/// Đối tượng JSON: các khoá theo thứ tự xuất hiện + bảng tra nhanh.
public struct JSONObject {
    public private(set) var keys: [String] = []
    public private(set) var values: [String: JSON] = [:]

    public init() {}

    public subscript(key: String) -> JSON? { values[key] }

    mutating func set(_ key: String, _ value: JSON) {
        if values.updateValue(value, forKey: key) == nil { keys.append(key) }
    }

    /// Các cặp (khoá, giá trị) theo đúng thứ tự.
    public var entries: [(String, JSON)] { keys.compactMap { k in values[k].map { (k, $0) } } }
}

public struct JSONError: Error, CustomStringConvertible {
    public let description: String
}

// MARK: - Truy cập

public extension JSON {
    subscript(key: String) -> JSON? {
        if case .object(let o) = self { return o[key] }
        return nil
    }

    subscript(index: Int) -> JSON? {
        if case .array(let a) = self, index >= 0, index < a.count { return a[index] }
        return nil
    }

    var objectValue: JSONObject? { if case .object(let o) = self { return o }; return nil }
    var isObject: Bool { objectValue != nil }
    var arrayValue: [JSON]? { if case .array(let a) = self { return a }; return nil }

    /// Chuỗi (kể cả chuỗi rỗng). Không chuyển đổi số.
    var rawString: String? { if case .string(let s) = self { return s }; return nil }

    /// Giống optString của org.json: số và bool cũng được đổi thành chuỗi.
    var stringValue: String? {
        switch self {
        case .string(let s): return s
        case .number(let d):
            if d.rounded() == d, abs(d) < 1e15 { return String(Int64(d)) }
            return String(d)
        case .bool(let b): return b ? "true" : "false"
        default: return nil
        }
    }

    var doubleValue: Double? {
        switch self {
        case .number(let d): return d
        case .string(let s): return Double(s.trimmingCharacters(in: .whitespaces))
        default: return nil
        }
    }

    /// Số nguyên; chấp nhận cả chuỗi số ("213") như YouTube hay dùng.
    var int64Value: Int64? {
        switch self {
        case .number(let d): return d.isFinite ? Int64(d) : nil
        case .string(let s): return Int64(s.trimmingCharacters(in: .whitespaces)) ?? Double(s).map { Int64($0) }
        default: return nil
        }
    }

    var intValue: Int? { int64Value.map { Int(truncatingIfNeeded: $0) } }

    var boolValue: Bool? {
        switch self {
        case .bool(let b): return b
        case .string(let s): return s == "true" ? true : (s == "false" ? false : nil)
        default: return nil
        }
    }

    /// Đi theo đường dẫn khoá; trả về nil nếu đứt giữa chừng.
    func at(_ path: [String]) -> JSON? {
        var cur: JSON? = self
        for p in path { cur = cur?[p]; if cur == nil { return nil } }
        return cur
    }

    /// Đối tượng tại đường dẫn (giống JSONObject.o(...) bên Kotlin).
    func o(_ path: String...) -> JSON? {
        guard let v = at(path), v.isObject else { return nil }
        return v
    }

    /// Mảng tại đường dẫn.
    func a(_ path: String...) -> [JSON]? { at(path)?.arrayValue }

    /// Chuỗi khác rỗng tại đường dẫn.
    func s(_ path: String...) -> String? {
        guard let v = at(path)?.rawString, !v.isEmpty else { return nil }
        return v
    }

    func has(_ key: String) -> Bool { self[key] != nil }
}

// MARK: - Bộ đọc JSON

struct JSONByteParser {
    let bytes: [UInt8]
    var i = 0

    init(bytes: [UInt8]) { self.bytes = bytes }

    mutating func parseDocument() throws -> JSON {
        // Bỏ BOM nếu có.
        if bytes.count >= 3, bytes[0] == 0xEF, bytes[1] == 0xBB, bytes[2] == 0xBF { i = 3 }
        skipWhitespace()
        let v = try parseValue(depth: 0)
        skipWhitespace()
        if i != bytes.count { throw JSONError(description: "Dư ký tự ở vị trí \(i)") }
        return v
    }

    private mutating func skipWhitespace() {
        while i < bytes.count {
            switch bytes[i] {
            case 0x20, 0x0A, 0x0D, 0x09: i += 1
            default: return
            }
        }
    }

    private mutating func parseValue(depth: Int) throws -> JSON {
        guard depth < 512 else { throw JSONError(description: "JSON lồng quá sâu") }
        guard i < bytes.count else { throw JSONError(description: "Hết dữ liệu") }
        switch bytes[i] {
        case UInt8(ascii: "{"): return try parseObject(depth: depth)
        case UInt8(ascii: "["): return try parseArray(depth: depth)
        case UInt8(ascii: "\""): return .string(try parseString())
        case UInt8(ascii: "t"): try expect("true"); return .bool(true)
        case UInt8(ascii: "f"): try expect("false"); return .bool(false)
        case UInt8(ascii: "n"): try expect("null"); return .null
        default: return try parseNumber()
        }
    }

    private mutating func expect(_ word: String) throws {
        for b in word.utf8 {
            guard i < bytes.count, bytes[i] == b else { throw JSONError(description: "Sai cú pháp ở vị trí \(i)") }
            i += 1
        }
    }

    private mutating func parseObject(depth: Int) throws -> JSON {
        i += 1
        var obj = JSONObject()
        skipWhitespace()
        if i < bytes.count, bytes[i] == UInt8(ascii: "}") { i += 1; return .object(obj) }
        while true {
            skipWhitespace()
            guard i < bytes.count, bytes[i] == UInt8(ascii: "\"") else { throw JSONError(description: "Cần khoá ở vị trí \(i)") }
            let key = try parseString()
            skipWhitespace()
            guard i < bytes.count, bytes[i] == UInt8(ascii: ":") else { throw JSONError(description: "Cần ':' ở vị trí \(i)") }
            i += 1
            skipWhitespace()
            obj.set(key, try parseValue(depth: depth + 1))
            skipWhitespace()
            guard i < bytes.count else { throw JSONError(description: "Hết dữ liệu trong đối tượng") }
            if bytes[i] == UInt8(ascii: ",") { i += 1; continue }
            if bytes[i] == UInt8(ascii: "}") { i += 1; return .object(obj) }
            throw JSONError(description: "Cần ',' hoặc '}' ở vị trí \(i)")
        }
    }

    private mutating func parseArray(depth: Int) throws -> JSON {
        i += 1
        var arr: [JSON] = []
        skipWhitespace()
        if i < bytes.count, bytes[i] == UInt8(ascii: "]") { i += 1; return .array(arr) }
        while true {
            skipWhitespace()
            arr.append(try parseValue(depth: depth + 1))
            skipWhitespace()
            guard i < bytes.count else { throw JSONError(description: "Hết dữ liệu trong mảng") }
            if bytes[i] == UInt8(ascii: ",") { i += 1; continue }
            if bytes[i] == UInt8(ascii: "]") { i += 1; return .array(arr) }
            throw JSONError(description: "Cần ',' hoặc ']' ở vị trí \(i)")
        }
    }

    private mutating func parseNumber() throws -> JSON {
        let start = i
        while i < bytes.count {
            let b = bytes[i]
            if (b >= 0x30 && b <= 0x39) || b == UInt8(ascii: "-") || b == UInt8(ascii: "+") || b == UInt8(ascii: ".")
                || b == UInt8(ascii: "e") || b == UInt8(ascii: "E") {
                i += 1
            } else {
                break
            }
        }
        guard i > start, let d = Double(String(decoding: bytes[start..<i], as: UTF8.self)) else {
            throw JSONError(description: "Số không hợp lệ ở vị trí \(start)")
        }
        return .number(d)
    }

    private mutating func parseString() throws -> String {
        i += 1 // dấu "
        var out: [UInt8] = []
        var runStart = i
        while i < bytes.count {
            let b = bytes[i]
            if b == UInt8(ascii: "\"") {
                out.append(contentsOf: bytes[runStart..<i])
                i += 1
                return String(decoding: out, as: UTF8.self)
            }
            if b == UInt8(ascii: "\\") {
                out.append(contentsOf: bytes[runStart..<i])
                i += 1
                guard i < bytes.count else { break }
                let e = bytes[i]
                i += 1
                switch e {
                case UInt8(ascii: "\""): out.append(0x22)
                case UInt8(ascii: "\\"): out.append(0x5C)
                case UInt8(ascii: "/"): out.append(0x2F)
                case UInt8(ascii: "b"): out.append(0x08)
                case UInt8(ascii: "f"): out.append(0x0C)
                case UInt8(ascii: "n"): out.append(0x0A)
                case UInt8(ascii: "r"): out.append(0x0D)
                case UInt8(ascii: "t"): out.append(0x09)
                case UInt8(ascii: "u"):
                    var code = try hex4()
                    if code >= 0xD800, code < 0xDC00,
                       i + 1 < bytes.count, bytes[i] == UInt8(ascii: "\\"), bytes[i + 1] == UInt8(ascii: "u") {
                        let save = i
                        i += 2
                        let low = try hex4()
                        if low >= 0xDC00, low < 0xE000 {
                            code = 0x10000 + ((code - 0xD800) << 10) + (low - 0xDC00)
                        } else {
                            i = save
                        }
                    }
                    let scalar = Unicode.Scalar(code) ?? "\u{FFFD}"
                    out.append(contentsOf: Array(String(Character(scalar)).utf8))
                default:
                    throw JSONError(description: "Ký tự thoát không hợp lệ ở vị trí \(i)")
                }
                runStart = i
                continue
            }
            i += 1
        }
        throw JSONError(description: "Chuỗi chưa đóng")
    }

    private mutating func hex4() throws -> UInt32 {
        guard i + 4 <= bytes.count else { throw JSONError(description: "\\u thiếu ký tự") }
        var v: UInt32 = 0
        for _ in 0..<4 {
            let b = bytes[i]
            let d: UInt32
            switch b {
            case 0x30...0x39: d = UInt32(b - 0x30)
            case 0x41...0x46: d = UInt32(b - 0x41 + 10)
            case 0x61...0x66: d = UInt32(b - 0x61 + 10)
            default: throw JSONError(description: "\\u không hợp lệ")
            }
            v = v << 4 | d
            i += 1
        }
        return v
    }
}

// MARK: - Các hàm giúp đọc JSON của YouTube (cấu trúc lồng rất sâu)

/// Đọc chữ ở các dạng YouTube hay dùng: simpleText, runs[], content.
public func textOf(_ v: JSON?) -> String? {
    guard let v else { return nil }
    var result: String?
    switch v {
    case .string(let s):
        result = s.isEmpty ? nil : s
    case .object:
        if let s = v["simpleText"]?.stringValue, !s.isEmpty {
            result = s
        } else if let runs = v["runs"]?.arrayValue {
            let joined = runs.map { $0["text"]?.stringValue ?? "" }.joined()
            result = joined.isEmpty ? nil : joined
        }
        if result == nil, let c = v["content"]?.stringValue, !c.isEmpty { result = c }
    default:
        result = nil
    }
    return result?.replacingOccurrences(of: "\u{00A0}", with: " ")
}

/// Tìm (theo chiều sâu, đúng thứ tự) đối tượng đầu tiên có khoá `key`.
public func findFirst(_ root: JSON?, _ key: String) -> JSON? {
    guard let root else { return nil }
    switch root {
    case .object(let o):
        if let hit = o[key], hit.isObject { return hit }
        for k in o.keys {
            if let found = findFirst(o[k], key) { return found }
        }
    case .array(let a):
        for v in a {
            if let found = findFirst(v, key) { return found }
        }
    default:
        break
    }
    return nil
}

/// Tìm tất cả đối tượng có khoá `key` (không đi sâu vào bên trong chính chúng).
public func findAll(_ root: JSON?, _ key: String) -> [JSON] {
    var out: [JSON] = []
    collect(root, key, &out)
    return out
}

private func collect(_ root: JSON?, _ key: String, _ out: inout [JSON]) {
    guard let root else { return }
    switch root {
    case .object(let o):
        if let hit = o[key], hit.isObject { out.append(hit) }
        for k in o.keys where k != key {
            collect(o[k], key, &out)
        }
    case .array(let a):
        for v in a { collect(v, key, &out) }
    default:
        break
    }
}

/// Chọn ảnh có chiều rộng gần `target` nhất trong mảng {url,width,height}.
public func bestImage(_ arr: [JSON]?, target: Int = 720) -> String? {
    guard let arr else { return nil }
    let list = arr.filter { !($0["url"]?.stringValue ?? "").isEmpty }
    guard !list.isEmpty else { return nil }
    let pick = list.min { a, b in
        distance(a, target) < distance(b, target)
    }
    return fixUrl(pick?["url"]?.stringValue)
}

private func distance(_ img: JSON, _ target: Int) -> Int {
    let w = img["width"]?.intValue ?? 0
    return w <= 0 ? Int.max / 2 : abs(w - target)
}

public func fixUrl(_ url: String?) -> String? {
    guard let url, !url.isEmpty else { return nil }
    if url.hasPrefix("//") { return "https:" + url }
    return url
}

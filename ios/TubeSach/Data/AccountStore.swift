import Foundation
import Security
import TubeSachCore

/// Lưu phiên đăng nhập Google (thử nghiệm) trên máy.
/// Cookie là thông tin đăng nhập nên nằm trong Keychain (chỉ máy này, không sao lưu iCloud).
enum AccountStore {
    private static let service = "com.luzart.tubesach.account"
    private static let account = "youtube-cookies"
    private static let infoKey = "accountInfo"

    static func loadCookies() -> [String: String]? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return try? JSONDecoder().decode([String: String].self, from: data)
    }

    static func saveCookies(_ cookies: [String: String]) {
        guard let data = try? JSONEncoder().encode(cookies) else { return }
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let attrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        if SecItemUpdate(base as CFDictionary, attrs as CFDictionary) == errSecItemNotFound {
            var add = base
            add.merge(attrs) { _, new in new }
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        UserDefaults.standard.removeObject(forKey: infoKey)
    }

    static func loadInfo() -> AccountInfo? {
        guard let data = UserDefaults.standard.data(forKey: infoKey) else { return nil }
        return try? JSONDecoder().decode(AccountInfo.self, from: data)
    }

    static func saveInfo(_ info: AccountInfo) {
        if let data = try? JSONEncoder().encode(info) { UserDefaults.standard.set(data, forKey: infoKey) }
    }

    /// Gắn phiên đăng nhập vào InnerTube; cookie xoay vòng được lưu lại.
    static func makeSession(_ cookies: [String: String]) -> AccountSession {
        let session = AccountSession(cookies: cookies)
        session.onChange = { saveCookies($0) }
        return session
    }
}

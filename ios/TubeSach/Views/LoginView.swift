import SwiftUI
import WebKit
import TubeSachCore

/// Trang đăng nhập Google (thử nghiệm). Người dùng đăng nhập trực tiếp trên trang của Google
/// trong WKWebView – app không thấy mật khẩu. Khi YouTube đã đặt cookie đăng nhập thì đóng lại.
struct LoginView: View {
    @Environment(\.dismiss) private var dismiss
    let onLoggedIn: ([String: String]) -> Void
    @State private var loading = true

    var body: some View {
        NavigationStack {
            LoginWebView(loading: $loading) { cookies in
                onLoggedIn(cookies)
                dismiss()
            }
            .overlay(alignment: .top) {
                if loading { ProgressView().padding() }
            }
            .navigationTitle("Đăng nhập Google")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Huỷ") { dismiss() }
                }
            }
        }
    }
}

struct LoginWebView: UIViewRepresentable {
    @Binding var loading: Bool
    let onDone: ([String: String]) -> Void

    /// Google từ chối đăng nhập nếu nhận ra trình duyệt nhúng, nên dùng chuỗi nhận dạng của Safari.
    static let safariUA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        // Kho riêng, không lưu lại: cookie cần thiết được app giữ trong Keychain.
        config.websiteDataStore = .nonPersistent()
        let web = WKWebView(frame: .zero, configuration: config)
        web.customUserAgent = Self.safariUA
        web.navigationDelegate = context.coordinator
        if let url = URL(string: AccountSession.loginURL) {
            web.load(URLRequest(url: url))
        }
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        private let parent: LoginWebView
        private var done = false

        init(_ parent: LoginWebView) {
            self.parent = parent
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            // Chỉ cho mở trang web (chặn intent://, itms-apps://…).
            let scheme = navigationAction.request.url?.scheme?.lowercased() ?? ""
            decisionHandler(scheme == "https" || scheme == "http" || scheme == "about" ? .allow : .cancel)
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            setLoading(true)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            setLoading(false)
            check(webView)
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            setLoading(false)
        }

        private func setLoading(_ value: Bool) {
            let parent = self.parent
            DispatchQueue.main.async { parent.loading = value }
        }

        /// Đã về YouTube và có cookie SAPISID → đăng nhập xong.
        private func check(_ webView: WKWebView) {
            guard !done, let host = webView.url?.host, AccountSession.isYoutube(host) else { return }
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { [weak self] cookies in
                var jar: [String: String] = [:]
                for c in cookies {
                    let domain = c.domain.hasPrefix(".") ? String(c.domain.dropFirst()) : c.domain
                    if AccountSession.isYoutube(domain) { jar[c.name] = c.value }
                }
                DispatchQueue.main.async {
                    guard let self, !self.done, AccountSession(cookies: jar).isValid else { return }
                    self.done = true
                    self.parent.onDone(jar)
                }
            }
        }
    }
}

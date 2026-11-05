import SwiftUI
import TubeSachCore

struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore
    @State private var confirmReset = false
    @State private var confirmClearHistory = false

    private let captionLanguages: [(String, String)] = [
        ("", "Tắt"), ("vi", "Tiếng Việt"), ("en", "Tiếng Anh"),
    ]

    var body: some View {
        Form {
            Section("Giao diện") {
                Picker("Giao diện", selection: $store.settings.theme) {
                    ForEach(ThemeMode.allCases) { Text($0.label).tag($0) }
                }
                Picker("Cỡ chữ", selection: $store.settings.textSize) {
                    ForEach(TextSize.allCases) { Text($0.label).tag($0) }
                }
                Toggle("Ẩn Shorts", isOn: $store.settings.hideShorts)
            }

            Section {
                Toggle("Tự động phát video tiếp theo", isOn: $store.settings.autoplay)
                Toggle("Phát khi tắt màn hình", isOn: $store.settings.backgroundPlay)
                Toggle("Hình trong hình khi về màn hình chính", isOn: $store.settings.autoPip)
                Picker("Chất lượng", selection: $store.settings.quality) {
                    ForEach(QualityMode.allCases) { Text($0.label).tag($0) }
                }
                if store.settings.quality == .fixed {
                    Picker("Độ phân giải tối đa", selection: $store.settings.fixedHeight) {
                        ForEach([1080, 720, 480, 360, 240, 144], id: \.self) { Text("\($0)p").tag($0) }
                    }
                }
                Picker("Tốc độ phát mặc định", selection: $store.settings.playbackSpeed) {
                    ForEach([Float(0.75), 1.0, 1.25, 1.5, 2.0], id: \.self) { s in
                        Text(s == 1.0 ? "Bình thường" : "\(String(format: "%g", s))x").tag(s)
                    }
                }
                Picker("Phụ đề tự bật", selection: $store.settings.captionLanguage) {
                    ForEach(captionLanguages, id: \.0) { code, name in
                        Text(name).tag(code)
                    }
                }
            } header: {
                Text("Phát video")
            } footer: {
                Text(store.settings.quality.detail)
            }

            Section {
                Toggle("Bật SponsorBlock", isOn: $store.settings.sponsorBlock)
                if store.settings.sponsorBlock {
                    ForEach(SegmentCategory.allCases) { cat in
                        Toggle(isOn: categoryBinding(cat)) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(cat.label)
                                Text(cat.detail).font(.caption).foregroundColor(.secondary)
                            }
                        }
                    }
                }
            } header: {
                Text("SponsorBlock")
            } footer: {
                Text("Cộng đồng đánh dấu đoạn tài trợ trong video; app tự bỏ qua. Chỉ gửi 4 ký tự đầu của mã băm video nên máy chủ không biết bạn xem gì.")
            }

            Section {
                Toggle("Hiện số lượt không thích", isOn: $store.settings.returnDislike)
            } footer: {
                Text("Dùng dữ liệu của Return YouTube Dislike.")
            }

            Section {
                Toggle("Đề xuất theo video đã xem", isOn: $store.settings.personalize)
                Button("Làm mới đề xuất trên trang chủ") { confirmReset = true }
                Button("Xoá video đã xem", role: .destructive) { confirmClearHistory = true }
                Button("Xoá lịch sử tìm kiếm", role: .destructive) { store.clearRecentSearches() }
            } header: {
                Text("Quyền riêng tư")
            } footer: {
                Text("Không cần tài khoản Google. App dùng một mã khách ẩn danh (giống trình duyệt chưa đăng nhập) để YouTube đề xuất video tiếng Việt. Lịch sử, kênh đăng ký chỉ lưu trên máy này.")
            }

            Section("Giới thiệu") {
                LabeledContent("Phiên bản", value: Services.appVersion)
                Button("Kiểm tra bản mới") { model.checkUpdate(manual: true) }
                if let msg = model.updateMessage {
                    Text(msg).font(.footnote).foregroundColor(.secondary)
                }
                Text("Dùng cho mục đích cá nhân. App không liên kết với YouTube hay Google. Hãy ủng hộ người làm video bạn yêu thích.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
        }
        .navigationTitle("Cài đặt")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Làm mới đề xuất?", isPresented: $confirmReset, titleVisibility: .visible) {
            Button("Làm mới", role: .destructive) { model.resetRecommendations() }
            Button("Huỷ", role: .cancel) {}
        } message: {
            Text("Trang chủ sẽ như máy mới, đề xuất làm lại từ đầu.")
        }
        .confirmationDialog("Xoá toàn bộ video đã xem?", isPresented: $confirmClearHistory, titleVisibility: .visible) {
            Button("Xoá hết", role: .destructive) { store.clearHistory() }
            Button("Huỷ", role: .cancel) {}
        }
    }

    private func categoryBinding(_ cat: SegmentCategory) -> Binding<Bool> {
        Binding(
            get: { store.settings.sponsorCategories.contains(cat.rawValue) },
            set: { on in
                if on {
                    store.settings.sponsorCategories.insert(cat.rawValue)
                } else {
                    store.settings.sponsorCategories.remove(cat.rawValue)
                }
            }
        )
    }
}

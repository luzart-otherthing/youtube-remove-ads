import SwiftUI
import TubeSachCore

/// Trang chủ: hàng chip, đề xuất của YouTube, kệ Shorts. Kéo xuống để làm mới, cuộn mãi không hết.
struct HomeView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if !model.home.chips.isEmpty {
                    ChipBar(chips: model.home.chips.map(\.title), selected: model.home.selectedChip) { i in
                        model.selectChip(i)
                    }
                }
                content
            }
        }
        .refreshable { await model.refreshHome() }
        .mainToolbar()
        .onAppear { model.loadHomeIfNeeded() }
    }

    @ViewBuilder
    private var content: some View {
        let home = model.home
        if home.loading && home.entries.isEmpty {
            LoadingView()
        } else if let err = home.error, home.entries.isEmpty {
            ErrorView(message: err) { model.refreshHomeInBackground() }
        } else if home.entries.isEmpty {
            // Lần đầu mở (giống YouTube chưa đăng nhập): trang chủ trống → gợi ý tìm kiếm + thịnh hành.
            VStack(alignment: .leading, spacing: 8) {
                MessageView(
                    icon: "magnifyingglass",
                    title: "Thử tìm kiếm để bắt đầu",
                    message: "Xem vài video, trang chủ sẽ có đề xuất tiếng Việt hợp với bạn.",
                    actionTitle: "Tìm kiếm",
                    action: { model.openSearch() }
                )
                if !model.homeTrending.isEmpty {
                    Label("Thịnh hành tại Việt Nam", systemImage: "flame.fill")
                        .font(.title3.bold())
                        .padding(.horizontal)
                    FeedList(entries: model.homeTrending.map { .video($0) })
                }
            }
        } else {
            FeedList(
                entries: home.entries,
                loadingMore: home.loadingMore,
                onLoadMore: home.continuation == nil ? nil : { model.loadMoreHome() }
            )
        }
    }
}

/// Hàng chip cuộn ngang ("Tất cả", "Âm nhạc", "Tin tức"…).
struct ChipBar: View {
    let chips: [String]
    let selected: Int
    let onSelect: (Int) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(chips.enumerated()), id: \.offset) { i, title in
                    Button { onSelect(i) } label: {
                        Text(title)
                            .font(.subheadline.weight(.semibold))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .foregroundColor(i == selected ? Color(.systemBackground) : .primary)
                            .background(i == selected ? Color.primary : Color(.secondarySystemFill), in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }
}

import SwiftUI
import TubeSachCore

/// Trang kênh: ảnh bìa, ảnh đại diện, @tên, số người đăng ký, tab Video / Trực tiếp.
struct ChannelView: View {
    @EnvironmentObject var model: AppModel
    let channelId: String

    var body: some View {
        ScrollView {
            if let st = model.channels[channelId] {
                if let page = st.page {
                    VStack(alignment: .leading, spacing: 0) {
                        header(page)
                        Picker("Mục", selection: Binding(
                            get: { st.tab },
                            set: { model.loadChannelTab(channelId, tab: $0) }
                        )) {
                            Text("Video").tag(0)
                            if page.liveParams != nil { Text("Trực tiếp").tag(1) }
                        }
                        .pickerStyle(.segmented)
                        .padding()
                        videos(st.videos)
                    }
                } else if let err = st.error {
                    ErrorView(message: err) { model.reloadChannel(channelId) }
                } else {
                    LoadingView()
                }
            } else {
                LoadingView()
            }
        }
        .navigationTitle(model.channels[channelId]?.page?.channel.name ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { model.loadChannel(channelId) }
    }

    @ViewBuilder
    private func header(_ page: ChannelPage) -> some View {
        if let banner = page.banner {
            Color.clear
                .aspectRatio(6, contentMode: .fit)
                .overlay { RemoteImage(url: banner) }
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal)
        }
        HStack(alignment: .center, spacing: 16) {
            Avatar(url: page.channel.avatar, size: 80)
            VStack(alignment: .leading, spacing: 4) {
                Text(page.channel.name).font(.title2.bold())
                let line = [page.handle, page.subscriberText, page.videoCountText].compactMap { $0 }.joined(separator: " · ")
                if !line.isEmpty {
                    Text(line).font(.subheadline).foregroundColor(.secondary)
                }
            }
        }
        .padding()
        if let d = page.description, !d.isEmpty {
            Text(d)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .lineLimit(3)
                .padding(.horizontal)
        }
        SubscribeButton(channel: page.channel)
            .padding(.horizontal)
            .padding(.top, 12)
    }

    @ViewBuilder
    private func videos(_ feed: FeedState) -> some View {
        if feed.loading {
            LoadingView()
        } else if let err = feed.error {
            ErrorView(message: err) { model.loadChannelTab(channelId, tab: model.channels[channelId]?.tab ?? 0) }
        } else if feed.entries.isEmpty {
            MessageView(icon: "video.slash", title: "Kênh này chưa có video")
        } else {
            FeedList(
                entries: feed.entries,
                showChannel: false,
                loadingMore: feed.loadingMore,
                onLoadMore: feed.continuation == nil ? nil : { model.loadMoreChannel(channelId) }
            )
        }
    }
}

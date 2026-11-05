import SwiftUI
import TubeSachCore

@MainActor
final class SearchViewModel: ObservableObject {
    @Published var text = ""
    @Published private(set) var suggestions: [String] = []
    @Published private(set) var query: String?
    @Published private(set) var filter: SearchFilter = .all
    @Published private(set) var results = FeedState()
    private var suggestTask: Task<Void, Never>?
    private var searchTask: Task<Void, Never>?

    func textChanged() {
        suggestTask?.cancel()
        let q = text
        guard !q.trimmingCharacters(in: .whitespaces).isEmpty else {
            suggestions = []
            return
        }
        suggestTask = Task {
            try? await Task.sleep(nanoseconds: 200_000_000)
            if Task.isCancelled { return }
            let s = await Services.youtube.suggestions(q)
            if !Task.isCancelled { suggestions = s }
        }
    }

    func search(_ q: String, filter: SearchFilter = .all) {
        let query = q.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return }
        text = query
        self.query = query
        self.filter = filter
        suggestTask?.cancel()
        searchTask?.cancel()
        results = FeedState(loading: true)
        searchTask = Task {
            do {
                let page = try await Services.youtube.search(query, filter: filter)
                if Task.isCancelled { return }
                results = FeedState(entries: page.entries, continuation: page.continuation, loaded: true)
            } catch {
                if Task.isCancelled { return }
                results = FeedState(error: friendlyError(error), loaded: true)
            }
        }
    }

    func loadMore() {
        guard let token = results.continuation, !results.loadingMore, !results.loading else { return }
        results.loadingMore = true
        Task {
            let page = try? await Services.youtube.searchMore(token)
            let keys = Set(results.entries.map(\.key))
            results.entries += (page?.entries ?? []).filter { !keys.contains($0.key) }
            results.continuation = page?.continuation
            results.loadingMore = false
        }
    }
}

/// Tìm kiếm: ô nhập có gợi ý, lịch sử tìm kiếm, bộ lọc, kết quả.
/// Dán link YouTube (từ Zalo, Facebook…) vào ô tìm kiếm để mở thẳng video.
struct SearchView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore
    @StateObject private var vm = SearchViewModel()
    @FocusState private var focused: Bool
    let initialText: String

    var body: some View {
        VStack(spacing: 0) {
            searchBar
            if focused || vm.query == nil {
                suggestionList
            } else {
                resultList
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if vm.query == nil && vm.text.isEmpty {
                vm.text = initialText
                focused = true
            }
        }
    }

    private var searchBar: some View {
        HStack(spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass").foregroundColor(.secondary)
                TextField("Tìm kiếm trên YouTube", text: $vm.text)
                    .focused($focused)
                    .submitLabel(.search)
                    .autocorrectionDisabled()
                    .onSubmit { submit(vm.text) }
                    .onChange(of: vm.text) { _ in vm.textChanged() }
                if !vm.text.isEmpty {
                    Button { vm.text = ""; focused = true } label: {
                        Image(systemName: "xmark.circle.fill").foregroundColor(.secondary)
                    }
                    .accessibilityLabel("Xoá chữ")
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 10)
            .background(Color(.secondarySystemFill), in: Capsule())
            PasteButton(payloadType: String.self) { strings in
                if let s = strings.first {
                    DispatchQueue.main.async { submit(s) }
                }
            }
            .labelStyle(.iconOnly)
            .buttonBorderShape(.capsule)
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }

    private func submit(_ text: String) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        if let id = Format.youtubeVideoId(inText: t) {
            focused = false
            model.play(videoId: id)
            return
        }
        store.addRecentSearch(t)
        focused = false
        vm.search(t)
    }

    private var suggestionList: some View {
        List {
            if vm.text.trimmingCharacters(in: .whitespaces).isEmpty {
                if !store.recentSearches.isEmpty {
                    Section {
                        ForEach(store.recentSearches, id: \.self) { q in
                            suggestionRow(q, icon: "clock.arrow.circlepath")
                                .swipeActions {
                                    Button(role: .destructive) { store.removeRecentSearch(q) } label: {
                                        Label("Xoá", systemImage: "trash")
                                    }
                                }
                        }
                    } header: {
                        HStack {
                            Text("Tìm kiếm gần đây")
                            Spacer()
                            Button("Xoá tất cả") { store.clearRecentSearches() }
                                .font(.footnote)
                        }
                    }
                } else {
                    Text("Gõ tên bài hát, ca sĩ, chương trình… hoặc dán link YouTube.")
                        .foregroundColor(.secondary)
                }
            } else {
                ForEach(vm.suggestions, id: \.self) { s in
                    suggestionRow(s, icon: "magnifyingglass")
                }
            }
        }
        .listStyle(.plain)
        .scrollDismissesKeyboard(.immediately)
    }

    private func suggestionRow(_ text: String, icon: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon).foregroundColor(.secondary)
            Button {
                submit(text)
            } label: {
                Text(text)
                    .foregroundColor(.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button {
                vm.text = text + " "
                focused = true
            } label: {
                Image(systemName: "arrow.up.left").foregroundColor(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Điền vào ô tìm kiếm")
        }
        .padding(.vertical, 6)
    }

    private var resultList: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ChipBar(chips: SearchFilter.allCases.map(\.label), selected: SearchFilter.allCases.firstIndex(of: vm.filter) ?? 0) { i in
                    if let q = vm.query { vm.search(q, filter: SearchFilter.allCases[i]) }
                }
                let r = vm.results
                if r.loading {
                    LoadingView()
                } else if let err = r.error {
                    ErrorView(message: err) { if let q = vm.query { vm.search(q, filter: vm.filter) } }
                } else if r.entries.isEmpty {
                    MessageView(icon: "magnifyingglass", title: "Không tìm thấy kết quả")
                } else {
                    FeedList(
                        entries: r.entries,
                        loadingMore: r.loadingMore,
                        onLoadMore: r.continuation == nil ? nil : { vm.loadMore() }
                    )
                }
            }
        }
        .scrollDismissesKeyboard(.immediately)
    }
}

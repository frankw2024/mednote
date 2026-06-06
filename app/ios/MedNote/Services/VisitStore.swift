import Foundation

@MainActor
final class VisitStore: ObservableObject {
    @Published private(set) var visits: [Visit] = []

    private let fileURL: URL

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        fileURL = docs.appendingPathComponent("visits.json")
        load()
    }

    func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let decoded = try? JSONDecoder().decode([Visit].self, from: data) else { return }
        visits = decoded
    }

    func save() {
        guard let data = try? JSONEncoder().encode(visits) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    func add(_ visit: Visit) {
        visits.insert(visit, at: 0)
        save()
    }

    func update(_ visit: Visit) {
        guard let idx = visits.firstIndex(where: { $0.id == visit.id }) else { return }
        visits[idx] = visit
        save()
    }

    func delete(_ visit: Visit) {
        if let name = visit.audioFileName {
            let url = audioDirectory.appendingPathComponent(name)
            try? FileManager.default.removeItem(at: url)
        }
        visits.removeAll { $0.id == visit.id }
        save()
    }

    var audioDirectory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("Recordings", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func audioURL(for visit: Visit) -> URL? {
        guard let name = visit.audioFileName else { return nil }
        return audioDirectory.appendingPathComponent(name)
    }

    func storeAudio(from source: URL, visitId: String) throws -> String {
        let ext = source.pathExtension.isEmpty ? "m4a" : source.pathExtension
        let name = "\(visitId).\(ext)"
        let dest = audioDirectory.appendingPathComponent(name)
        if source.path == dest.path { return name }
        if FileManager.default.fileExists(atPath: dest.path) {
            try FileManager.default.removeItem(at: dest)
        }
        try FileManager.default.copyItem(at: source, to: dest)
        return name
    }
}

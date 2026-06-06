import SwiftUI

@main
struct MedNoteApp: App {
    @StateObject private var settings = SettingsStore()
    @StateObject private var visitStore = VisitStore()
    @StateObject private var pipeline = VisitPipeline()
    @StateObject private var recorder = AudioRecorderService()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(settings)
                .environmentObject(visitStore)
                .environmentObject(pipeline)
                .environmentObject(recorder)
        }
    }
}

import SwiftUI

struct ContentView: View {
    @State private var tab = 0

    var body: some View {
        TabView(selection: $tab) {
            HomeView()
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(0)

            CallVisitView()
                .tabItem { Label("Call", systemImage: "phone.fill") }
                .tag(1)

            RecordView()
                .tabItem { Label("Record", systemImage: "mic.fill") }
                .tag(2)

            VisitsListView()
                .tabItem { Label("Visits", systemImage: "list.bullet.rectangle") }
                .tag(3)

            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                .tag(4)
        }
        .tint(Color(red: 0.39, green: 0.4, blue: 0.95))
    }
}

#Preview {
    ContentView()
        .environmentObject(SettingsStore())
        .environmentObject(VisitStore())
        .environmentObject(VisitPipeline())
        .environmentObject(AudioRecorderService())
}

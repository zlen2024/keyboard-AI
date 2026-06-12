import SwiftUI

struct ContentView: View {

    @State private var testText = ""

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    Text("Add Keyboard AI to your iPhone, then use it in any app.")
                        .foregroundColor(.secondary)

                    step(
                        number: 1,
                        title: "Open keyboard settings",
                        detail: "Settings → General → Keyboard → Keyboards → Add New Keyboard… and choose “Keyboard AI”."
                    )

                    Button {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        Text("Open Settings")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                    }
                    .buttonStyle(.borderedProminent)

                    step(
                        number: 2,
                        title: "Switch to Keyboard AI",
                        detail: "In any app, tap a text field, then hold the 🌐 globe key and pick “Keyboard AI”."
                    )

                    step(
                        number: 3,
                        title: "Try it out",
                        detail: "Tap below and start typing."
                    )

                    TextEditor(text: $testText)
                        .frame(height: 120)
                        .padding(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.secondary.opacity(0.4))
                        )
                }
                .padding(24)
            }
            .navigationTitle("Keyboard AI")
        }
        .navigationViewStyle(.stack)
    }

    private func step(number: Int, title: String, detail: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("\(number). \(title)")
                .font(.headline)
            Text(detail)
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }
}

#Preview {
    ContentView()
}

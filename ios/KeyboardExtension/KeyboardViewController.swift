import UIKit

final class KeyboardViewController: UIInputViewController {

    private enum LayerKind {
        case letters, symbols, symbolsShifted
    }

    private var keyboardView: KeyboardView!
    private var heightConstraint: NSLayoutConstraint?
    private var lastShiftTap: TimeInterval = 0
    private var layerKind: LayerKind = .letters {
        didSet {
            switch layerKind {
            case .letters: keyboardView.keyLayer = KeyboardLayouts.letters
            case .symbols: keyboardView.keyLayer = KeyboardLayouts.symbols
            case .symbolsShifted: keyboardView.keyLayer = KeyboardLayouts.symbolsShifted
            }
        }
    }

    private static let doubleTapWindow: TimeInterval = 0.3

    override func viewDidLoad() {
        super.viewDidLoad()

        keyboardView = KeyboardView(frame: .zero)
        keyboardView.delegate = self
        keyboardView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(keyboardView)

        NSLayoutConstraint.activate([
            keyboardView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            keyboardView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            keyboardView.topAnchor.constraint(equalTo: view.topAnchor),
            keyboardView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if heightConstraint == nil {
            let constraint = view.heightAnchor.constraint(
                equalToConstant: keyboardView.preferredHeight
            )
            constraint.priority = .init(999)
            constraint.isActive = true
            heightConstraint = constraint
        }
        configureForCurrentInput()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        updateAutoShift()
    }

    private func configureForCurrentInput() {
        let proxy = textDocumentProxy

        switch proxy.keyboardType {
        case .numberPad, .phonePad, .decimalPad, .numbersAndPunctuation:
            layerKind = .symbols
        default:
            layerKind = .letters
        }

        keyboardView.enterLabel = enterLabel(for: proxy.returnKeyType ?? .default)
        keyboardView.shiftState = .off
        updateAutoShift()
    }

    private func enterLabel(for returnKey: UIReturnKeyType) -> String {
        switch returnKey {
        case .go: return "go"
        case .search, .google, .yahoo: return "search"
        case .send: return "send"
        case .next: return "next"
        case .done: return "done"
        case .join: return "join"
        case .emergencyCall: return "call"
        case .continue: return "continue"
        default: return "return"
        }
    }

    // MARK: - Shift / auto-capitalization

    private func handleShift() {
        let now = Date.timeIntervalSinceReferenceDate
        let isDoubleTap = now - lastShiftTap < Self.doubleTapWindow
        lastShiftTap = now

        switch keyboardView.shiftState {
        case .off:
            keyboardView.shiftState = .shifted
        case .shifted:
            keyboardView.shiftState = isDoubleTap ? .capsLock : .off
        case .capsLock:
            keyboardView.shiftState = .off
        }
    }

    private func updateAutoShift() {
        guard keyboardView.shiftState != .capsLock else { return }

        let proxy = textDocumentProxy
        guard proxy.autocapitalizationType == .sentences ||
            proxy.autocapitalizationType == .words ||
            proxy.autocapitalizationType == .allCharacters
        else { return }

        let before = proxy.documentContextBeforeInput ?? ""
        let shouldShift: Bool
        switch proxy.autocapitalizationType {
        case .allCharacters:
            shouldShift = true
        case .words:
            shouldShift = before.isEmpty || before.hasSuffix(" ") || before.hasSuffix("\n")
        default: // .sentences
            let trimmed = before.trimmingCharacters(in: .whitespaces)
            shouldShift = before.isEmpty
                || before.hasSuffix("\n")
                || (before.hasSuffix(" ") && (trimmed.hasSuffix(".")
                    || trimmed.hasSuffix("!") || trimmed.hasSuffix("?")))
        }
        keyboardView.shiftState = shouldShift ? .shifted : .off
    }

    // MARK: - Layer switching

    private func toggleSymbols() {
        if layerKind == .letters {
            layerKind = .symbols
        } else {
            layerKind = .letters
            updateAutoShift()
        }
    }

    private func toggleSymbolsPage() {
        layerKind = layerKind == .symbols ? .symbolsShifted : .symbols
    }
}

// MARK: - KeyboardViewDelegate

extension KeyboardViewController: KeyboardViewDelegate {

    func keyboardView(_ view: KeyboardView, didTap key: Key) {
        let proxy = textDocumentProxy

        switch key.action {
        case .character(let c):
            let text = view.shiftState == .off ? c : c.uppercased()
            proxy.insertText(text)
            if view.shiftState == .shifted {
                view.shiftState = .off
            }
            updateAutoShift()
        case .space:
            proxy.insertText(" ")
            updateAutoShift()
        case .enter:
            proxy.insertText("\n")
        case .delete:
            proxy.deleteBackward()
            updateAutoShift()
        case .shift:
            handleShift()
        case .modeChange:
            toggleSymbols()
        case .symbolsShift:
            toggleSymbolsPage()
        case .globe:
            break // handled by the system via the configured button
        }
    }

    func keyboardViewDidRepeatDelete(_ view: KeyboardView) {
        textDocumentProxy.deleteBackward()
    }

    func keyboardView(_ view: KeyboardView, configureGlobeButton button: UIButton) {
        button.addTarget(
            self,
            action: #selector(handleInputModeList(from:with:)),
            for: .allTouchEvents
        )
    }

}

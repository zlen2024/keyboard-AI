import UIKit

enum ShiftState {
    case off, shifted, capsLock
}

protocol KeyboardViewDelegate: AnyObject {
    func keyboardView(_ view: KeyboardView, didTap key: Key)
    func keyboardViewDidRepeatDelete(_ view: KeyboardView)
    /// The globe key must receive raw touch events so the system can show
    /// the input-mode list on long press.
    func keyboardView(_ view: KeyboardView, configureGlobeButton button: UIButton)
}

/// Renders one KeyboardLayer as rows of buttons, laid out manually from the
/// keys' width weights. Visuals only — input logic lives in the controller,
/// which mutates `layer`, `shiftState` and `enterLabel`.
final class KeyboardView: UIView {

    weak var delegate: KeyboardViewDelegate?

    var keyLayer: KeyboardLayer = KeyboardLayouts.letters {
        didSet { rebuildButtons() }
    }

    var shiftState: ShiftState = .off {
        didSet { refreshTitles() }
    }

    var enterLabel: String = "return" {
        didSet { refreshTitles() }
    }

    private struct Slot {
        let key: Key
        let button: UIButton
    }

    private var slots: [Slot] = []
    private var deleteTimer: Timer?
    private(set) var deleteDidRepeat = false

    private let rowHeight: CGFloat = 54
    private let keyGap: CGFloat = 5
    private let sidePadding: CGFloat = 3
    private let verticalPadding: CGFloat = 6

    private let backgroundDark = UIColor(red: 0.106, green: 0.114, blue: 0.129, alpha: 1)
    private let keyColor = UIColor(red: 0.227, green: 0.239, blue: 0.267, alpha: 1)
    private let specialKeyColor = UIColor(red: 0.165, green: 0.176, blue: 0.20, alpha: 1)
    private let accentColor = UIColor(red: 0.29, green: 0.55, blue: 0.97, alpha: 1)
    private let pressedColor = UIColor(red: 0.353, green: 0.369, blue: 0.40, alpha: 1)

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = backgroundDark
        rebuildButtons()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    var preferredHeight: CGFloat {
        rowHeight * CGFloat(keyLayer.rows.count) + 2 * verticalPadding
    }

    // MARK: - Building

    private func rebuildButtons() {
        slots.forEach { $0.button.removeFromSuperview() }
        slots = []

        for row in keyLayer.rows {
            for key in row {
                let button = makeButton(for: key)
                addSubview(button)
                slots.append(Slot(key: key, button: button))
            }
        }
        refreshTitles()
        setNeedsLayout()
    }

    private func makeButton(for key: Key) -> UIButton {
        let button = UIButton(type: .custom)
        button.layer.cornerRadius = 7
        button.titleLabel?.adjustsFontSizeToFitWidth = true
        button.titleLabel?.minimumScaleFactor = 0.5
        button.setTitleColor(.white, for: .normal)
        button.backgroundColor = baseColor(for: key)

        switch key.action {
        case .globe:
            delegate?.keyboardView(self, configureGlobeButton: button)
        case .delete:
            button.addTarget(self, action: #selector(deleteTouchDown(_:)), for: .touchDown)
            button.addTarget(
                self, action: #selector(deleteTouchUp(_:)),
                for: [.touchUpInside, .touchUpOutside, .touchCancel, .touchDragExit]
            )
        default:
            button.addTarget(self, action: #selector(keyTouchDown(_:)), for: .touchDown)
            button.addTarget(self, action: #selector(keyTouchUp(_:)), for: .touchUpInside)
            button.addTarget(
                self, action: #selector(keyTouchCancelled(_:)),
                for: [.touchUpOutside, .touchCancel, .touchDragExit]
            )
        }
        return button
    }

    private func baseColor(for key: Key) -> UIColor {
        switch key.action {
        case .enter:
            return accentColor
        case .shift where shiftState != .off:
            return accentColor
        case .character, .space:
            return keyColor
        default:
            return specialKeyColor
        }
    }

    private func refreshTitles() {
        for slot in slots {
            let key = slot.key
            let title: String
            let fontSize: CGFloat

            switch key.action {
            case .character(let c):
                title = shiftState == .off ? c : c.uppercased()
                fontSize = 22
            case .enter:
                title = enterLabel
                fontSize = 16
            case .shift:
                title = shiftState == .capsLock ? "⇪" : "⇧"
                fontSize = 20
            case .space:
                title = key.label
                fontSize = 13
            default:
                title = key.label
                fontSize = 16
            }

            slot.button.setTitle(title, for: .normal)
            slot.button.titleLabel?.font = .systemFont(ofSize: fontSize, weight: .regular)
            slot.button.backgroundColor = baseColor(for: key)
            if case .space = key.action {
                slot.button.setTitleColor(UIColor.white.withAlphaComponent(0.6), for: .normal)
            }
        }
    }

    // MARK: - Layout

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0 else { return }

        let usableWidth = bounds.width - 2 * sidePadding
        let maxWeight = keyLayer.rows
            .map { $0.reduce(CGFloat(0)) { $0 + $1.widthWeight } }
            .max() ?? 1
        let unit = usableWidth / maxWeight

        var index = 0
        for (rowIndex, row) in keyLayer.rows.enumerated() {
            let rowWeight = row.reduce(CGFloat(0)) { $0 + $1.widthWeight }
            var x = sidePadding + (usableWidth - rowWeight * unit) / 2
            let y = verticalPadding + CGFloat(rowIndex) * rowHeight

            for key in row {
                let w = key.widthWeight * unit
                slots[index].button.frame = CGRect(
                    x: x + keyGap / 2,
                    y: y + keyGap / 2,
                    width: w - keyGap,
                    height: rowHeight - keyGap
                )
                x += w
                index += 1
            }
        }
    }

    // MARK: - Touch handling

    @objc private func keyTouchDown(_ sender: UIButton) {
        sender.backgroundColor = pressedColor
        UIDevice.current.playInputClick()
    }

    @objc private func keyTouchUp(_ sender: UIButton) {
        restoreColor(of: sender)
        if let slot = slots.first(where: { $0.button === sender }) {
            delegate?.keyboardView(self, didTap: slot.key)
        }
    }

    @objc private func keyTouchCancelled(_ sender: UIButton) {
        restoreColor(of: sender)
    }

    @objc private func deleteTouchDown(_ sender: UIButton) {
        sender.backgroundColor = pressedColor
        UIDevice.current.playInputClick()
        deleteDidRepeat = false
        deleteTimer = Timer.scheduledTimer(withTimeInterval: 0.35, repeats: false) { [weak self] _ in
            guard let self else { return }
            self.deleteTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
                guard let self else { return }
                self.deleteDidRepeat = true
                self.delegate?.keyboardViewDidRepeatDelete(self)
            }
        }
    }

    @objc private func deleteTouchUp(_ sender: UIButton) {
        restoreColor(of: sender)
        deleteTimer?.invalidate()
        deleteTimer = nil
        if !deleteDidRepeat, let slot = slots.first(where: { $0.button === sender }) {
            delegate?.keyboardView(self, didTap: slot.key)
        }
        deleteDidRepeat = false
    }

    private func restoreColor(of button: UIButton) {
        if let slot = slots.first(where: { $0.button === button }) {
            button.backgroundColor = baseColor(for: slot.key)
        }
    }

    override var canBecomeFirstResponder: Bool { true }
}

extension KeyboardView: UIInputViewAudioFeedback {
    var enableInputClicksWhenVisible: Bool { true }
}

import Foundation

enum KeyAction: Equatable {
    case character(String)
    case shift
    case delete
    case modeChange      // letters <-> symbols
    case symbolsShift    // symbols page 1 <-> page 2
    case space
    case enter
    case globe           // next keyboard
}

struct Key {
    let action: KeyAction
    let label: String
    let widthWeight: CGFloat

    init(_ action: KeyAction, label: String, widthWeight: CGFloat = 1) {
        self.action = action
        self.label = label
        self.widthWeight = widthWeight
    }

    static func char(_ c: String) -> Key {
        Key(.character(c), label: c)
    }
}

/// Rows narrower than the widest row are centered by the view.
struct KeyboardLayer {
    let rows: [[Key]]
}

enum KeyboardLayouts {

    private static func charRow(_ chars: String) -> [Key] {
        chars.map { Key.char(String($0)) }
    }

    private static let shift = Key(.shift, label: "⇧", widthWeight: 1.5)
    private static let delete = Key(.delete, label: "⌫", widthWeight: 1.5)
    private static let space = Key(.space, label: "Keyboard AI", widthWeight: 4)
    private static let enter = Key(.enter, label: "return", widthWeight: 1.5)
    private static let globe = Key(.globe, label: "🌐")
    private static let toSymbols = Key(.modeChange, label: "?123", widthWeight: 1.5)
    private static let toLetters = Key(.modeChange, label: "ABC", widthWeight: 1.5)
    private static let toSymbols2 = Key(.symbolsShift, label: "=\\<", widthWeight: 1.5)
    private static let toSymbols1 = Key(.symbolsShift, label: "?123", widthWeight: 1.5)

    static let letters = KeyboardLayer(rows: [
        charRow("qwertyuiop"),
        charRow("asdfghjkl"),
        [shift] + charRow("zxcvbnm") + [delete],
        [toSymbols, .char(","), globe, space, .char("."), enter],
    ])

    static let symbols = KeyboardLayer(rows: [
        charRow("1234567890"),
        charRow("@#$_&-+()/"),
        [toSymbols2] + charRow("*\"':;!?") + [delete],
        [toLetters, .char(","), globe, space, .char("."), enter],
    ])

    static let symbolsShifted = KeyboardLayer(rows: [
        charRow("~`|•√π÷×¶∆"),
        charRow("£€¥^°={}\\"),
        [toSymbols1] + charRow("%©®™✓[]") + [delete],
        [toLetters, .char("<"), globe, space, .char(">"), enter],
    ])
}

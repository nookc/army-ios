import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

enum KeyboardAdjustmentMode: Equatable {
    case none
    case position
    case size
}

private enum KeyboardResizeAxis {
    case horizontal
    case vertical
}

private final class KeyboardGroupResizePreview: ObservableObject {
    struct Snapshot: Equatable {
        var isActive = false
        var widthRatio: CGFloat = 1
        var heightRatio: CGFloat = 1
    }

    @Published private(set) var snapshot = Snapshot()

    private var axis: KeyboardResizeAxis?
    private var targetScale: GameProfile.KeyboardGroupScale?

    func update(
        translation: CGSize,
        baseScale: GameProfile.KeyboardGroupScale,
        divisor: CGFloat,
        matchingScales: [Double]
    ) {
        if axis == nil {
            guard max(abs(translation.width), abs(translation.height)) >= 3 else {
                return
            }
            axis = abs(translation.width) >= abs(translation.height)
                ? .horizontal
                : .vertical
        }

        var result = baseScale
        switch axis {
        case .horizontal:
            result.width = snappedScale(
                baseScale.width + Double(translation.width / max(divisor, 1)),
                matchingScales: matchingScales
            )
        case .vertical:
            result.height = snappedScale(
                baseScale.height + Double(translation.height / max(divisor, 1)),
                matchingScales: matchingScales
            )
        case nil:
            return
        }

        targetScale = result
        let nextSnapshot = Snapshot(
            isActive: true,
            widthRatio: CGFloat(result.width / max(baseScale.width, 0.001)),
            heightRatio: CGFloat(result.height / max(baseScale.height, 0.001))
        )
        if snapshot != nextSnapshot {
            snapshot = nextSnapshot
        }
    }

    func finish() -> GameProfile.KeyboardGroupScale? {
        let result = targetScale
        reset()
        return result
    }

    func reset() {
        axis = nil
        targetScale = nil
        if snapshot != Snapshot() {
            snapshot = Snapshot()
        }
    }

    private func snappedScale(
        _ scale: Double,
        matchingScales: [Double]
    ) -> Double {
        let gridSize = 0.05
        let clamped = min(max(scale, 0.5), 1.8)
        let gridScale = (clamped / gridSize).rounded() * gridSize
        if abs(gridScale - 1) <= gridSize {
            return 1
        }
        if let match = matchingScales.first(where: {
            abs($0 - gridScale) <= gridSize / 2
        }) {
            return match
        }
        return gridScale
    }
}

private final class KeyboardEditPreviewStore: ObservableObject {
    let softKeys = KeyboardGroupResizePreview()
    let numbers = KeyboardGroupResizePreview()
    let directions = KeyboardGroupResizePreview()

    func preview(for groupID: String) -> KeyboardGroupResizePreview {
        switch groupID {
        case "soft-keys": return softKeys
        case "numbers": return numbers
        default: return directions
        }
    }

    func resetAll() {
        softKeys.reset()
        numbers.reset()
        directions.reset()
    }
}

@MainActor
private final class VirtualKeyboardControlPressState: ObservableObject {
    @Published var isPressed = false
}

@MainActor
private final class VirtualKeyboardInputCoordinator: ObservableObject {
    private var heldControls: [String: [J2MEKey]] = [:]
    private var keyOwnerCounts: [J2MEKey: Int] = [:]
    private var controlStates: [String: VirtualKeyboardControlPressState] = [:]
    private weak var activeSession: EmulatorSession?
#if canImport(UIKit)
    private let lightHaptic = UIImpactFeedbackGenerator(style: .light)
    private let mediumHaptic = UIImpactFeedbackGenerator(style: .medium)
#endif

    func state(for controlID: String) -> VirtualKeyboardControlPressState {
        if let state = controlStates[controlID] {
            return state
        }
        let state = VirtualKeyboardControlPressState()
        controlStates[controlID] = state
        return state
    }

    @discardableResult
    func press(
        controlID: String,
        keys: [J2MEKey],
        session: EmulatorSession
    ) -> Bool {
        guard heldControls[controlID] == nil else { return false }
        if let activeSession, activeSession !== session {
            _ = releaseAll(session: activeSession)
        }
        activeSession = session

        var seenKeys = Set<J2MEKey>()
        let uniqueKeys = keys.filter { seenKeys.insert($0).inserted }
        guard !uniqueKeys.isEmpty else { return false }

        heldControls[controlID] = uniqueKeys
        state(for: controlID).isPressed = true
        for key in uniqueKeys {
            let ownerCount = keyOwnerCounts[key, default: 0]
            keyOwnerCounts[key] = ownerCount + 1
            if ownerCount == 0 {
                session.send(key, pressed: true)
            }
        }
        return true
    }

    @discardableResult
    func release(
        controlID: String,
        session: EmulatorSession
    ) -> Bool {
        guard let keys = heldControls.removeValue(forKey: controlID) else {
            return false
        }
        state(for: controlID).isPressed = false
        let targetSession = activeSession ?? session

        for key in keys {
            guard let ownerCount = keyOwnerCounts[key] else { continue }
            if ownerCount <= 1 {
                keyOwnerCounts.removeValue(forKey: key)
                targetSession.send(key, pressed: false)
            } else {
                keyOwnerCounts[key] = ownerCount - 1
            }
        }
        if heldControls.isEmpty {
            activeSession = nil
        }
        return true
    }

#if canImport(UIKit)
    func performHaptic(emphasized: Bool) {
        let generator = emphasized ? mediumHaptic : lightHaptic
        generator.impactOccurred()
        generator.prepare()
    }
#endif

    @discardableResult
    func releaseAll(session: EmulatorSession) -> Int {
        let releasedControlCount = heldControls.count
        let targetSession = activeSession ?? session
        let heldControlIDs = Array(heldControls.keys)
        heldControls.removeAll(keepingCapacity: true)
        for controlID in heldControlIDs {
            state(for: controlID).isPressed = false
        }

        let keys = keyOwnerCounts.keys.sorted { $0.rawValue < $1.rawValue }
        keyOwnerCounts.removeAll(keepingCapacity: true)
        activeSession = nil
        for key in keys {
            targetSession.send(key, pressed: false)
        }
        return releasedControlCount
    }
}

struct KeyboardControlDescriptor: Identifiable, Equatable {
    let id: String
    let label: String
    let keys: [J2MEKey]
    let accessibilityLabel: String
    let emphasized: Bool
    let groupID: String
    let column: Int
    let row: Int
}

struct KeyboardLayoutDefinition {
    let metricColumns: Int
    let contentColumns: Int
    let rows: Int
    let keyHeightFactor: CGFloat
    let controls: [KeyboardControlDescriptor]
}

enum KeyboardLayoutCatalog {
    static func definition(for profile: GameProfile) -> KeyboardLayoutDefinition {
        definition(for: profile.resolvedKeyboardBaseType)
    }

    static func definition(
        for type: GameProfile.VirtualKeyboardType
    ) -> KeyboardLayoutDefinition {
        switch type == .custom ? .arrowsNumbers : type {
        case .numbersArrows:
            return split(numbersFirst: true)
        case .arrowsNumbers:
            return split(numbersFirst: false)
        case .phone:
            return phone(arrows: false)
        case .phoneArrows:
            return phone(arrows: true)
        case .numbers:
            return numbersOnly()
        case .arrows:
            return arrowsOnly()
        case .custom:
            return split(numbersFirst: false)
        }
    }

    static func selectableLayouts(
        hasCustomLayout: Bool
    ) -> [GameProfile.VirtualKeyboardType] {
        var layouts: [GameProfile.VirtualKeyboardType] = [
            .phone,
            .phoneArrows,
            .numbersArrows,
            .arrowsNumbers,
            .numbers,
            .arrows
        ]
        if hasCustomLayout {
            layouts.insert(.custom, at: 0)
        }
        return layouts
    }

    static func controlChoices(for profile: GameProfile) -> [KeyboardControlDescriptor] {
        definition(for: profile).controls
    }

    private static func split(numbersFirst: Bool) -> KeyboardLayoutDefinition {
        let numberStart = numbersFirst ? 0 : 3
        let directionStart = numbersFirst ? 3 : 0
        return KeyboardLayoutDefinition(
            metricColumns: 6,
            contentColumns: 6,
            rows: 4,
            keyHeightFactor: 0.75,
            controls: numberControls(startColumn: numberStart)
                + directionControls(startColumn: directionStart)
        )
    }

    private static func phone(arrows: Bool) -> KeyboardLayoutDefinition {
        let topMiddle = arrows
            ? descriptor(
                id: "menu",
                label: "M",
                keys: [],
                accessibilityLabel: L10n.string("Menu"),
                emphasized: false,
                groupID: "soft-keys",
                column: 1,
                row: 0
            )
            : key(.fire, label: "F", groupID: "soft-keys", column: 1, row: 0)

        var controls: [KeyboardControlDescriptor] = [
            key(.softLeft, groupID: "soft-keys", column: 0, row: 0),
            topMiddle,
            key(.softRight, groupID: "soft-keys", column: 2, row: 0)
        ]

        if arrows {
            controls += [
                key(.one, groupID: "numbers", column: 0, row: 1),
                key(.up, label: "↑", groupID: "directions", column: 1, row: 1),
                key(.three, groupID: "numbers", column: 2, row: 1),
                key(.left, label: "←", groupID: "directions", column: 0, row: 2),
                key(.fire, label: "F", groupID: "directions", column: 1, row: 2),
                key(.right, label: "→", groupID: "directions", column: 2, row: 2),
                key(.seven, groupID: "numbers", column: 0, row: 3),
                key(.down, label: "↓", groupID: "directions", column: 1, row: 3),
                key(.nine, groupID: "numbers", column: 2, row: 3),
                key(.star, groupID: "numbers", column: 0, row: 4),
                key(.zero, groupID: "numbers", column: 1, row: 4),
                key(.pound, groupID: "numbers", column: 2, row: 4)
            ]
        } else {
            let rows: [[J2MEKey]] = [
                [.one, .two, .three],
                [.four, .five, .six],
                [.seven, .eight, .nine],
                [.star, .zero, .pound]
            ]
            for (rowIndex, row) in rows.enumerated() {
                for (column, keyValue) in row.enumerated() {
                    controls.append(
                        key(
                            keyValue,
                            groupID: "numbers",
                            column: column,
                            row: rowIndex + 1
                        )
                    )
                }
            }
        }

        return KeyboardLayoutDefinition(
            metricColumns: 3,
            contentColumns: 3,
            rows: 5,
            keyHeightFactor: 0.5625,
            controls: controls
        )
    }

    private static func numbersOnly() -> KeyboardLayoutDefinition {
        var controls: [KeyboardControlDescriptor] = [
            key(.softLeft, groupID: "soft-keys", column: 0, row: 0),
            key(.softRight, groupID: "soft-keys", column: 4, row: 0)
        ]
        let rows: [[J2MEKey]] = [
            [.one, .two, .three],
            [.four, .five, .six],
            [.seven, .eight, .nine],
            [.star, .zero, .pound]
        ]
        for (rowIndex, row) in rows.enumerated() {
            for (index, keyValue) in row.enumerated() {
                controls.append(
                    key(
                        keyValue,
                        groupID: "numbers",
                        column: index + 1,
                        row: rowIndex
                    )
                )
            }
        }
        return KeyboardLayoutDefinition(
            metricColumns: 6,
            contentColumns: 5,
            rows: 4,
            keyHeightFactor: 0.75,
            controls: controls
        )
    }

    /// Army2.5.0: 2 phim mem hai ben (OK = phim mem trai, Back = phim mem
    /// phai), 4 phim huong va nut Ban o giua (giu de tang luc, tha ra la ban).
    /// Moi nut gui nhan khi cham va nha khi buong, giong phim that.
    private static func arrowsOnly() -> KeyboardLayoutDefinition {
        let controls: [KeyboardControlDescriptor] = [
            key(.softLeft, label: "OK", groupID: "soft-keys", column: 0, row: 0),
            key(.up, label: "▲", groupID: "directions", column: 2, row: 0),
            key(.softRight, label: "Back", groupID: "soft-keys", column: 4, row: 0),
            key(.left, label: "◀", groupID: "directions", column: 1, row: 1),
            key(.fire, label: "Bắn", groupID: "directions", column: 2, row: 1),
            key(.right, label: "▶", groupID: "directions", column: 3, row: 1),
            key(.down, label: "▼", groupID: "directions", column: 2, row: 2)
        ]
        return KeyboardLayoutDefinition(
            metricColumns: 5,
            contentColumns: 5,
            rows: 3,
            keyHeightFactor: 0.8,
            controls: controls
        )
    }

    private static func numberControls(startColumn: Int) -> [KeyboardControlDescriptor] {
        let rows: [[J2MEKey]] = [
            [.one, .two, .three],
            [.four, .five, .six],
            [.seven, .eight, .nine],
            [.star, .zero, .pound]
        ]
        return rows.enumerated().flatMap { rowIndex, row in
            row.enumerated().map { column, keyValue in
                key(
                    keyValue,
                    groupID: "numbers",
                    column: startColumn + column,
                    row: rowIndex
                )
            }
        }
    }

    private static func directionControls(startColumn: Int) -> [KeyboardControlDescriptor] {
        [
            key(.softLeft, groupID: "soft-keys", column: startColumn, row: 0),
            key(.softRight, groupID: "soft-keys", column: startColumn + 2, row: 0),
            diagonal("↖", [.up, .left], id: "up-left", column: startColumn, row: 1),
            key(.up, label: "↑", groupID: "directions", column: startColumn + 1, row: 1),
            diagonal("↗", [.up, .right], id: "up-right", column: startColumn + 2, row: 1),
            key(.left, label: "←", groupID: "directions", column: startColumn, row: 2),
            key(.fire, label: "F", groupID: "directions", column: startColumn + 1, row: 2),
            key(.right, label: "→", groupID: "directions", column: startColumn + 2, row: 2),
            diagonal("↙", [.down, .left], id: "down-left", column: startColumn, row: 3),
            key(.down, label: "↓", groupID: "directions", column: startColumn + 1, row: 3),
            diagonal("↘", [.down, .right], id: "down-right", column: startColumn + 2, row: 3)
        ]
    }

    private static func key(
        _ key: J2MEKey,
        label: String? = nil,
        groupID: String,
        column: Int,
        row: Int
    ) -> KeyboardControlDescriptor {
        descriptor(
            id: "key-\(key.rawValue)",
            label: label ?? key.title,
            keys: [key],
            accessibilityLabel: accessibilityLabel(for: key),
            emphasized: key == .fire,
            groupID: groupID,
            column: column,
            row: row
        )
    }

    private static func diagonal(
        _ label: String,
        _ keys: [J2MEKey],
        id: String,
        column: Int,
        row: Int
    ) -> KeyboardControlDescriptor {
        descriptor(
            id: id,
            label: label,
            keys: keys,
            accessibilityLabel: diagonalAccessibilityLabel(for: id),
            emphasized: false,
            groupID: "directions",
            column: column,
            row: row
        )
    }

    private static func descriptor(
        id: String,
        label: String,
        keys: [J2MEKey],
        accessibilityLabel: String,
        emphasized: Bool,
        groupID: String,
        column: Int,
        row: Int
    ) -> KeyboardControlDescriptor {
        KeyboardControlDescriptor(
            id: id,
            label: label,
            keys: keys,
            accessibilityLabel: accessibilityLabel,
            emphasized: emphasized,
            groupID: groupID,
            column: column,
            row: row
        )
    }

    private static func diagonalAccessibilityLabel(for id: String) -> String {
        switch id {
        case "up-left": return L10n.string("Up left")
        case "up-right": return L10n.string("Up right")
        case "down-left": return L10n.string("Down left")
        case "down-right": return L10n.string("Down right")
        default: return id.replacingOccurrences(of: "-", with: " ")
        }
    }

    private static func accessibilityLabel(for key: J2MEKey) -> String {
        switch key {
        case .up: return L10n.string("Up")
        case .down: return L10n.string("Down")
        case .left: return L10n.string("Left")
        case .right: return L10n.string("Right")
        case .fire: return L10n.string("Fire")
        case .softLeft: return L10n.string("Left soft key")
        case .softRight: return L10n.string("Right soft key")
        default: return key.title
        }
    }
}

struct KeypadView: View {
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var session: EmulatorSession

    @Binding var profile: GameProfile
    let editMode: KeyboardAdjustmentMode
    let layoutRect: CGRect
    let displayRect: CGRect
    let onKeyActivity: (Bool) -> Void
    let onObscuresDisplayChange: (Bool) -> Void

    // Keep live drag data outside the parent layout state. On iOS 16,
    // rebuilding the moving key hierarchy for every touch event can cancel
    // the active high-priority gesture and stall the main thread.
    @StateObject private var resizePreviews = KeyboardEditPreviewStore()
    @StateObject private var inputCoordinator = VirtualKeyboardInputCoordinator()
    @State private var inputResetGeneration: UInt = 0

    private let movementGridSize: CGFloat = 4

    var body: some View {
        GeometryReader { geometry in
            let definition = KeyboardLayoutCatalog.definition(for: profile)
            let customization = profile.effectiveKeyboardLayoutCustomization
            let matchingScales = customization.groupScales.values
                .flatMap { [$0.width, $0.height] }
            let frames = layoutFrames(
                definition: definition,
                size: geometry.size,
                layoutRect: layoutRect,
                customization: customization
            )
            let hidden = customization.hiddenControlIDs
            let visibleFrames = frames.filter { !hidden.contains($0.key) }
            let visibleControls = definition.controls.filter { !hidden.contains($0.id) }
            let obscuresDisplay = visibleFrames.values.contains { $0.intersects(displayRect) }

            ZStack(alignment: .topLeading) {
                ForEach(visibleControls) { control in
                    if let frame = frames[control.id] {
                        let groupScale = customization.groupScales[control.groupID]
                            ?? GameProfile.KeyboardGroupScale()
                        VirtualKeyButton(
                            control: control,
                            profile: profile,
                            editMode: editMode,
                            width: frame.width,
                            height: frame.height,
                            displayRect: displayRect,
                            obscuresDisplay: obscuresDisplay,
                            containerSize: geometry.size,
                            baseGroupScale: groupScale,
                            matchingGroupScales: matchingScales,
                            resizePreview: resizePreviews.preview(for: control.groupID),
                            movementGridSize: movementGridSize,
                            inputCoordinator: inputCoordinator,
                            pressState: inputCoordinator.state(for: control.id),
                            inputResetGeneration: inputResetGeneration,
                            onKeyActivity: onKeyActivity,
                            onPositionDragEnded: { translation in
                                commitControlPosition(
                                    control,
                                    translation: translation,
                                    definition: definition,
                                    size: geometry.size
                                )
                            },
                            onResizeDragEnded: { scale in
                                commitGroupScale(
                                    control.groupID,
                                    scale: scale
                                )
                            }
                        )
                        .environmentObject(session)
                        .position(x: frame.midX, y: frame.midY)
                    }
                }

#if canImport(UIKit)
                if editMode == .none {
                    // Keep native touch capture scoped to each visible key.
                    // A previous full-surface UIView router made key taps very
                    // reliable, but its UIKit host sat above FrameSurface and
                    // could consume hit testing for the J2ME canvas. Per-key
                    // capture preserves the stable UIKit touch lifecycle while
                    // leaving every gap between controls transparent to game
                    // pointerPressed/pointerDragged/pointerReleased events.
                    ForEach(visibleControls) { control in
                        if let frame = frames[control.id] {
                            VirtualKeyTouchCaptureView(
                                resetGeneration: inputResetGeneration,
                                onPressedChanged: { pressed in
                                    if pressed {
                                        pressVirtualControl(control)
                                    } else {
                                        releaseVirtualControl(control)
                                    }
                                }
                            )
                            .frame(width: frame.width, height: frame.height)
                            .position(x: frame.midX, y: frame.midY)
                        }
                    }
                }
#endif
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .onAppear {
                onObscuresDisplayChange(obscuresDisplay)
            }
            .onChange(of: editMode) { _ in
                resizePreviews.resetAll()
                releaseAllVirtualKeys()
            }
            .onChange(of: obscuresDisplay) { value in
                onObscuresDisplayChange(value)
            }
        }
        .onChange(of: scenePhase) { phase in
            if phase != .active {
                releaseAllVirtualKeys()
            }
        }
        .onChange(of: profile.virtualKeyboardType) { _ in
            releaseAllVirtualKeys()
        }
        .onDisappear {
            releaseAllVirtualKeys()
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Virtual keyboard")
    }

    private func releaseAllVirtualKeys() {
        let releasedControlCount = inputCoordinator.releaseAll(session: session)
        inputResetGeneration &+= 1
        for _ in 0..<releasedControlCount {
            onKeyActivity(false)
        }
    }

    private func pressVirtualControl(_ control: KeyboardControlDescriptor) {
        guard inputCoordinator.press(
            controlID: control.id,
            keys: control.keys,
            session: session
        ) else {
            return
        }
        onKeyActivity(true)
#if canImport(UIKit)
        if profile.hapticFeedback {
            inputCoordinator.performHaptic(emphasized: control.emphasized)
        }
#endif
    }

    private func releaseVirtualControl(_ control: KeyboardControlDescriptor) {
        guard inputCoordinator.release(
            controlID: control.id,
            session: session
        ) else {
            return
        }
        onKeyActivity(false)
    }

    private func layoutFrames(
        definition: KeyboardLayoutDefinition,
        size: CGSize,
        layoutRect: CGRect,
        customization: GameProfile.KeyboardLayoutCustomization
    ) -> [String: CGRect] {
        guard size.width > 0, size.height > 0 else { return [:] }

        let sizingSize = layoutRect.size
        let baseGap = min(8, max(4, sizingSize.width / 82))
        let gap = baseGap
        let unscaledWidth = max(
            28,
            (sizingSize.width - gap * CGFloat(definition.metricColumns - 1))
                / CGFloat(definition.metricColumns)
        )
        let availableHeight = max(
            28,
            (sizingSize.height - gap * CGFloat(definition.rows - 1))
                / CGFloat(definition.rows)
        )
        let keyWidth = unscaledWidth
        let keyHeight = min(
            unscaledWidth * definition.keyHeightFactor,
            availableHeight
        )
        let contentWidth = keyWidth * CGFloat(definition.contentColumns)
            + gap * CGFloat(definition.contentColumns - 1)
        let contentHeight = keyHeight * CGFloat(definition.rows)
            + gap * CGFloat(definition.rows - 1)
        let startX = layoutRect.minX + (layoutRect.width - contentWidth) / 2
        let startY = layoutRect.maxY - contentHeight

        var frames: [String: CGRect] = [:]
        for control in definition.controls {
            let groupScale = customization.groupScales[control.groupID]
                ?? GameProfile.KeyboardGroupScale()
            let offset = customization.controlOffsets[control.id]
                ?? GameProfile.KeyboardControlOffset()
            let columnOffset = CGFloat(control.column) * (keyWidth + gap)
            let rowOffset = CGFloat(control.row) * (keyHeight + gap)
            let centerX = startX + columnOffset + keyWidth / 2
                + CGFloat(offset.x) * size.width
            let centerY = startY + rowOffset + keyHeight / 2
                + CGFloat(offset.y) * size.height
            let scaledWidth = max(24, keyWidth * CGFloat(groupScale.width))
            let scaledHeight = max(18, keyHeight * CGFloat(groupScale.height))
            frames[control.id] = CGRect(
                x: centerX - scaledWidth / 2,
                y: centerY - scaledHeight / 2,
                width: scaledWidth,
                height: scaledHeight
            )
        }
        return frames
    }

    private func commitControlPosition(
        _ control: KeyboardControlDescriptor,
        translation: CGSize,
        definition: KeyboardLayoutDefinition,
        size: CGSize
    ) {
        guard size.width > 0, size.height > 0 else { return }

        let currentCustomization = profile.effectiveKeyboardLayoutCustomization
        let origin = currentCustomization.controlOffsets[control.id]
            ?? GameProfile.KeyboardControlOffset()
        let gridTranslation = CGSize(
            width: snapped(translation.width, step: movementGridSize),
            height: snapped(translation.height, step: movementGridSize)
        )
        var candidateOffset = GameProfile.KeyboardControlOffset(
            x: origin.x + Double(gridTranslation.width / size.width),
            y: origin.y + Double(gridTranslation.height / size.height)
        )
        var candidateCustomization = currentCustomization
        candidateCustomization.controlOffsets[control.id] = candidateOffset
        let candidateFrames = layoutFrames(
            definition: definition,
            size: size,
            layoutRect: layoutRect,
            customization: candidateCustomization
        )

        if let candidateFrame = candidateFrames[control.id] {
            let otherFrames = candidateFrames
                .filter {
                    $0.key != control.id
                        && !candidateCustomization.hiddenControlIDs.contains($0.key)
                }
                .map(\.value)
            let alignmentDelta = snapDelta(
                movingFrame: candidateFrame,
                otherFrames: otherFrames,
                containerSize: size
            )
            let alignedFrame = candidateFrame.offsetBy(
                dx: alignmentDelta.width,
                dy: alignmentDelta.height
            )
            let boundsDelta = containmentDelta(
                for: alignedFrame,
                in: CGRect(origin: .zero, size: size).insetBy(dx: 2, dy: 2)
            )
            candidateOffset.x += Double(
                (alignmentDelta.width + boundsDelta.width) / size.width
            )
            candidateOffset.y += Double(
                (alignmentDelta.height + boundsDelta.height) / size.height
            )
        }

        profile.updateKeyboardLayoutCustomization { customization in
            customization.controlOffsets[control.id] = candidateOffset.isDefault
                ? nil
                : candidateOffset
        }
    }

    private func commitGroupScale(
        _ groupID: String,
        scale: GameProfile.KeyboardGroupScale
    ) {
        profile.updateKeyboardLayoutCustomization { customization in
            customization.groupScales[groupID] = scale.isDefault ? nil : scale
        }
    }

    private func snapped(_ value: CGFloat, step: CGFloat) -> CGFloat {
        guard step > 0 else { return value }
        return (value / step).rounded() * step
    }

    private func containmentDelta(for frame: CGRect, in bounds: CGRect) -> CGSize {
        let horizontal: CGFloat
        if frame.width >= bounds.width {
            horizontal = bounds.midX - frame.midX
        } else if frame.minX < bounds.minX {
            horizontal = bounds.minX - frame.minX
        } else if frame.maxX > bounds.maxX {
            horizontal = bounds.maxX - frame.maxX
        } else {
            horizontal = 0
        }

        let vertical: CGFloat
        if frame.height >= bounds.height {
            vertical = bounds.midY - frame.midY
        } else if frame.minY < bounds.minY {
            vertical = bounds.minY - frame.minY
        } else if frame.maxY > bounds.maxY {
            vertical = bounds.maxY - frame.maxY
        } else {
            vertical = 0
        }
        return CGSize(width: horizontal, height: vertical)
    }

    private func snapDelta(
        movingFrame: CGRect,
        otherFrames: [CGRect],
        containerSize: CGSize
    ) -> CGSize {
        let radius: CGFloat = 9
        let movingX = [movingFrame.minX, movingFrame.midX, movingFrame.maxX]
        let movingY = [movingFrame.minY, movingFrame.midY, movingFrame.maxY]
        var targetX: [CGFloat] = [0, containerSize.width / 2, containerSize.width]
        var targetY: [CGFloat] = [0, containerSize.height / 2, containerSize.height]
        for frame in otherFrames {
            targetX += [frame.minX, frame.midX, frame.maxX]
            targetY += [frame.minY, frame.midY, frame.maxY]
        }
        return CGSize(
            width: closestSnapDelta(moving: movingX, targets: targetX, radius: radius),
            height: closestSnapDelta(moving: movingY, targets: targetY, radius: radius)
        )
    }

    private func closestSnapDelta(
        moving: [CGFloat],
        targets: [CGFloat],
        radius: CGFloat
    ) -> CGFloat {
        var best: CGFloat?
        for source in moving {
            for target in targets {
                let delta = target - source
                guard abs(delta) <= radius else { continue }
                if best == nil || abs(delta) < abs(best!) {
                    best = delta
                }
            }
        }
        return best ?? 0
    }
}

#if canImport(UIKit)
private struct VirtualKeyTouchCaptureView: UIViewRepresentable {
    let resetGeneration: UInt
    let onPressedChanged: (Bool) -> Void

    func makeUIView(context: Context) -> TouchCaptureView {
        let view = TouchCaptureView(frame: .zero)
        view.backgroundColor = .clear
        view.isOpaque = false
        view.isAccessibilityElement = false
        view.isMultipleTouchEnabled = true
        view.isExclusiveTouch = false
        view.onPressedChanged = onPressedChanged
        view.applyResetGeneration(resetGeneration)
        return view
    }

    func updateUIView(_ uiView: TouchCaptureView, context: Context) {
        uiView.onPressedChanged = onPressedChanged
        uiView.applyResetGeneration(resetGeneration)
    }

    final class TouchCaptureView: UIView {
        var onPressedChanged: ((Bool) -> Void)?

        private var activeTouchIDs = Set<ObjectIdentifier>()
        private var pressStartedAt: TimeInterval?
        private var pendingRelease: DispatchWorkItem?
        private var resetGeneration: UInt?
        private let minimumPressDuration: TimeInterval = 0.035

        func applyResetGeneration(_ generation: UInt) {
            guard resetGeneration != generation else { return }
            resetGeneration = generation
            pendingRelease?.cancel()
            pendingRelease = nil
            activeTouchIDs.removeAll(keepingCapacity: true)
            pressStartedAt = nil
        }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
            if let pendingRelease {
                pendingRelease.cancel()
                self.pendingRelease = nil
                pressStartedAt = nil
                onPressedChanged?(false)
            }

            let wasPressed = !activeTouchIDs.isEmpty
            for touch in touches {
                activeTouchIDs.insert(ObjectIdentifier(touch))
            }
            if !wasPressed, !activeTouchIDs.isEmpty {
                pressStartedAt = ProcessInfo.processInfo.systemUptime
                onPressedChanged?(true)
            }
        }

        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
            finish(touches)
        }

        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
            finish(touches)
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if window == nil {
                releaseImmediately()
            }
        }

        private func finish(_ touches: Set<UITouch>) {
            for touch in touches {
                activeTouchIDs.remove(ObjectIdentifier(touch))
            }
            guard activeTouchIDs.isEmpty else { return }
            scheduleRelease()
        }

        private func scheduleRelease() {
            let startedAt = pressStartedAt
                ?? ProcessInfo.processInfo.systemUptime
            let elapsed = ProcessInfo.processInfo.systemUptime - startedAt
            let remaining = minimumPressDuration - elapsed
            guard remaining > 0 else {
                releaseImmediately()
                return
            }

            pendingRelease?.cancel()
            let workItem = DispatchWorkItem { [weak self] in
                guard let self, self.activeTouchIDs.isEmpty else { return }
                self.pendingRelease = nil
                self.releaseImmediately()
            }
            pendingRelease = workItem
            DispatchQueue.main.asyncAfter(
                deadline: .now() + remaining,
                execute: workItem
            )
        }

        private func releaseImmediately() {
            let wasPressed = pressStartedAt != nil || !activeTouchIDs.isEmpty
            pendingRelease?.cancel()
            pendingRelease = nil
            activeTouchIDs.removeAll(keepingCapacity: true)
            pressStartedAt = nil
            if wasPressed {
                onPressedChanged?(false)
            }
        }
    }
}

private struct KeyboardEditPanGestureView: UIViewRepresentable {
    let onChanged: (CGSize) -> Void
    let onEnded: (CGSize) -> Void
    let onCancelled: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(
            onChanged: onChanged,
            onEnded: onEnded,
            onCancelled: onCancelled
        )
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .clear
        view.isOpaque = false
        view.isAccessibilityElement = false

        let recognizer = UIPanGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handlePan(_:))
        )
        recognizer.minimumNumberOfTouches = 1
        recognizer.maximumNumberOfTouches = 1
        recognizer.cancelsTouchesInView = true
        view.addGestureRecognizer(recognizer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onChanged = onChanged
        context.coordinator.onEnded = onEnded
        context.coordinator.onCancelled = onCancelled
    }

    final class Coordinator: NSObject {
        var onChanged: (CGSize) -> Void
        var onEnded: (CGSize) -> Void
        var onCancelled: () -> Void

        init(
            onChanged: @escaping (CGSize) -> Void,
            onEnded: @escaping (CGSize) -> Void,
            onCancelled: @escaping () -> Void
        ) {
            self.onChanged = onChanged
            self.onEnded = onEnded
            self.onCancelled = onCancelled
        }

        @objc func handlePan(_ recognizer: UIPanGestureRecognizer) {
            let translation = recognizer.translation(in: recognizer.view?.window)
            let size = CGSize(width: translation.x, height: translation.y)

            switch recognizer.state {
            case .began, .changed:
                onChanged(size)
            case .ended:
                onEnded(size)
            case .cancelled, .failed:
                onCancelled()
            default:
                break
            }
        }
    }
}
#endif

private struct VirtualKeyButton: View {
    @EnvironmentObject private var session: EmulatorSession
    @Environment(\.colorScheme) private var colorScheme

    let control: KeyboardControlDescriptor
    let profile: GameProfile
    let editMode: KeyboardAdjustmentMode
    let width: CGFloat
    let height: CGFloat
    let displayRect: CGRect
    let obscuresDisplay: Bool
    let containerSize: CGSize
    let baseGroupScale: GameProfile.KeyboardGroupScale
    let matchingGroupScales: [Double]
    @ObservedObject var resizePreview: KeyboardGroupResizePreview
    let movementGridSize: CGFloat
    let inputCoordinator: VirtualKeyboardInputCoordinator
    @ObservedObject var pressState: VirtualKeyboardControlPressState
    let inputResetGeneration: UInt
    let onKeyActivity: (Bool) -> Void
    let onPositionDragEnded: (CGSize) -> Void
    let onResizeDragEnded: (GameProfile.KeyboardGroupScale) -> Void

    @State private var isPressed = false
    @State private var editTranslation = CGSize.zero

    var body: some View {
        Group {
            if editMode == .none {
                GeometryReader { geometry in
                    keyContent(
                        effectiveOpacity: effectiveOpacity(
                            for: geometry.frame(in: .named("emulatorSurface"))
                        )
                    )
                }
            } else {
                // Editing always renders opaque controls and avoids one
                // GeometryReader per key while the drag gesture is active.
                keyContent(effectiveOpacity: 1)
            }
        }
        .frame(width: width, height: height)
        .scaleEffect(
            x: editMode == .size ? resizePreview.snapshot.widthRatio : 1,
            y: editMode == .size ? resizePreview.snapshot.heightRatio : 1,
            anchor: .center
        )
        .offset(positionPreviewTranslation)
        .onChange(of: inputResetGeneration) { _ in
            isPressed = false
        }
        .onDisappear {
            isPressed = false
            release()
        }
    }

    private func keyContent(effectiveOpacity: Double) -> some View {
        Text(control.label)
            .font(.system(size: min(width, height) * 0.36, weight: .medium))
            .foregroundStyle(labelColor)
            .frame(width: width, height: height)
            .background { buttonBackground(effectiveOpacity: effectiveOpacity) }
            .contentShape(Rectangle())
            .scaleEffect(effectivePressed && editMode == .none ? 0.96 : 1)
            .animation(.easeOut(duration: 0.06), value: effectivePressed)
            .overlay { keyboardInteractionLayer }
            .accessibilityLabel(control.accessibilityLabel)
            .accessibilityAddTraits(.isButton)
    }

    @ViewBuilder
    private var keyboardInteractionLayer: some View {
        if editMode == .none {
#if canImport(UIKit)
            // iOS uses a stable native capture view above each visible key.
            // Keeping capture outside the animated key content prevents a
            // press animation/layout update from disturbing the active touch.
            Color.clear
                .allowsHitTesting(false)
#else
            Color.clear
                .contentShape(Rectangle())
                .gesture(keyPressGesture)
#endif
        } else {
            editOutline
                .opacity(editMode == .size && !isGroupSelected ? 0.55 : 1)
            keyboardEditGestureLayer
        }
    }

#if !canImport(UIKit)
    private var keyPressGesture: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { _ in
                setPressed(true)
            }
            .onEnded { _ in
                setPressed(false)
            }
    }
#endif

    private func setPressed(_ pressed: Bool) {
        guard isPressed != pressed else { return }
        isPressed = pressed
        if pressed {
            press()
        } else {
            release()
        }
    }

    @ViewBuilder
    private var keyboardEditGestureLayer: some View {
#if canImport(UIKit)
        // SwiftUI on iOS 15/16 can cancel a DragGesture when the same view is
        // offset during the drag. UIPanGestureRecognizer retains ownership of
        // the touch while SwiftUI redraws the key preview.
        KeyboardEditPanGestureView(
            onChanged: handleEditDragChanged,
            onEnded: handleEditDragEnded,
            onCancelled: cancelEditDrag
        )
#else
        Color.clear
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 3, coordinateSpace: .global)
                    .onChanged { value in
                        handleEditDragChanged(value.translation)
                    }
                    .onEnded { value in
                        handleEditDragEnded(value.translation)
                    }
            )
#endif
    }

    private var effectivePressed: Bool {
#if canImport(UIKit)
        pressState.isPressed
#else
        isPressed
#endif
    }

    private func handleEditDragChanged(_ translation: CGSize) {
        switch editMode {
        case .none:
            break
        case .position:
            editTranslation = translation
        case .size:
            resizePreview.update(
                translation: translation,
                baseScale: baseGroupScale,
                divisor: min(containerSize.width, containerSize.height),
                matchingScales: matchingGroupScales
            )
        }
    }

    private func handleEditDragEnded(_ translation: CGSize) {
        switch editMode {
        case .none:
            break
        case .position:
            editTranslation = .zero
            onPositionDragEnded(translation)
        case .size:
            if let scale = resizePreview.finish() {
                onResizeDragEnded(scale)
            }
        }
    }

    private func cancelEditDrag() {
        editTranslation = .zero
        resizePreview.reset()
    }

    private var positionPreviewTranslation: CGSize {
        guard editMode == .position else { return .zero }
        return CGSize(
            width: snapped(editTranslation.width, step: movementGridSize),
            height: snapped(editTranslation.height, step: movementGridSize)
        )
    }

    private var isGroupSelected: Bool {
        editMode == .size && resizePreview.snapshot.isActive
    }

    private func snapped(_ value: CGFloat, step: CGFloat) -> CGFloat {
        guard step > 0 else { return value }
        return (value / step).rounded() * step
    }

    private var editOutline: some View {
        RoundedRectangle(cornerRadius: 6, style: .continuous)
            .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 2, dash: [5, 3]))
            .padding(2)
    }

    @ViewBuilder
    private func buttonBackground(effectiveOpacity: Double) -> some View {
        switch profile.buttonShape {
        case .oval:
            styledBackground(Capsule(), effectiveOpacity: effectiveOpacity)
        case .rectangle:
            styledBackground(Rectangle(), effectiveOpacity: effectiveOpacity)
        case .roundedRectangle:
            styledBackground(
                RoundedRectangle(cornerRadius: min(width, height) * 0.24, style: .continuous),
                effectiveOpacity: effectiveOpacity
            )
        }
    }

    @ViewBuilder
    private func styledBackground<S: Shape>(
        _ shape: S,
        effectiveOpacity: Double
    ) -> some View {
        let selected = effectivePressed || isGroupSelected
        if profile.usesNativeKeyboardPalette {
            let neutralOverlay = Color.primary.opacity(colorScheme == .dark ? 0.10 : 0.04)
            let pressedFill = Color.accentColor.opacity(max(effectiveOpacity, 0.72))
            let outline = Color.primary.opacity(colorScheme == .dark ? 0.24 : 0.16)

            shape
                .fill(.thinMaterial)
                .opacity(effectiveOpacity)
                .overlay(shape.fill(selected ? pressedFill : neutralOverlay))
                .overlay(shape.stroke(outline, lineWidth: 0.75))
        } else {
            let fillHex = selected
                ? profile.keyboardSelectedBackgroundHex
                : profile.keyboardBackgroundHex
            let fill = (Color(j2meHex: fillHex) ?? .gray).opacity(effectiveOpacity)
            let outline = (Color(j2meHex: profile.keyboardOutlineHex) ?? .white)
                .opacity(effectiveOpacity)

            shape
                .fill(fill)
                .overlay(shape.stroke(outline, lineWidth: 1))
        }
    }

    private func effectiveOpacity(for keyFrame: CGRect) -> Double {
        if editMode != .none {
            return 1
        }
        guard obscuresDisplay else { return 1 }
        if profile.forceOpacityForOffscreenKeys, !keyFrame.intersects(displayRect) {
            return 1
        }
        return profile.keyboardOpacity
    }

    private var labelColor: Color {
        let selected = effectivePressed || isGroupSelected
        if profile.usesNativeKeyboardPalette {
            return selected ? .white : .primary
        }
        let hex = selected
            ? profile.keyboardSelectedForegroundHex
            : profile.keyboardForegroundHex
        return Color(j2meHex: hex) ?? .primary
    }

    private func press() {
        guard inputCoordinator.press(
            controlID: control.id,
            keys: control.keys,
            session: session
        ) else {
            return
        }
        onKeyActivity(true)
#if canImport(UIKit)
        if profile.hapticFeedback {
            inputCoordinator.performHaptic(emphasized: control.emphasized)
        }
#endif
    }

    private func release() {
        guard inputCoordinator.release(
            controlID: control.id,
            session: session
        ) else {
            return
        }
        onKeyActivity(false)
    }
}

private extension Color {
    init?(j2meHex: String) {
        let cleaned = j2meHex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        guard cleaned.count == 6, let value = UInt64(cleaned, radix: 16) else { return nil }
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}


// MARK: - Army2.5.0: ban phim 4x2 duoi man hinh doc
//
//   F1 (phim mem trai) | ▲ | LUC MAX | F2 (phim mem phai)
//   ◀                  | ▼ |    ▶    | OK (ban / chon)
//
// Moi o gui nhan khi cham va nha khi buong (giu OK de tang luc, buong la ban).
// LUC MAX: giu phim ban du lau de luc len toi da (game tu ban khi day luc).
// Truot ngon tay giua cac phim huong doi phim ngay, khong can nhac tay.

struct Army2KeypadView: View {
    @EnvironmentObject private var session: EmulatorSession

    static let columns = 4
    static let rows = 2

    var body: some View {
#if canImport(UIKit)
        Army2PadRepresentable { key, pressed in
            session.send(key, pressed: pressed)
        }
        .aspectRatio(
            CGFloat(Self.columns) / CGFloat(Self.rows),
            contentMode: .fit
        )
        .frame(maxWidth: .infinity)
        .background(Color.white.ignoresSafeArea(edges: .bottom))
#else
        EmptyView()
#endif
    }
}

#if canImport(UIKit)
private enum Army2KeypadLabels {
    // chu vang cua game (font/font -> fcg14m.png), ve san thanh PNG nho
    static let f1 = "iVBORw0KGgoAAAANSUhEUgAAAA4AAAANCAYAAACZ3F9/AAAAWklEQVR42rWQsQkAMQwDta7G+H1+nd/ky4BDClW5kCLEoEa+A2PZLkmYsYN9OlU9lIDYn4pvwqcCsxc1Zi8CcEf8EjwVGS6nWYl/giIwp2KrBEVmbK8ekkGmA+NdNXYox4jOAAAAAElFTkSuQmCC"
    static let f2 = "iVBORw0KGgoAAAANSUhEUgAAABIAAAANCAYAAACkTj4ZAAAAdUlEQVR42p2T2wmAMBAEr92UYT+2Yyd+CitCYAJ3cUMO5sddh5BHtNYUESU9C9vpIx01FH3HishtL6QT8rJznr950UeWexFQYJBsibyEzb5gdvz0YOzUImZJgugGRDmnAwg3RBNx6BEkETkA/5T3hBnz3/f2AvXCn2XyKtfOAAAAAElFTkSuQmCC"
    static let ok = "iVBORw0KGgoAAAANSUhEUgAAABMAAAANCAYAAABLjFUnAAAAcklEQVR42rWTwQnAMAwDva7G6D5dp5v0WXDRp8LI4EeawD2kJBfycHAByIho4Z7OlJ6U/B3Ko0dC60nNKpz+IlF22SmY5873AazIXLQgM9EO2SWYx05IuC5zYeYtmOeuzSYzxgeUNQFP9ugL1pOSf53NF9V35P4CqFKZAAAAAElFTkSuQmCC"
    static let lucMax = "iVBORw0KGgoAAAANSUhEUgAAAEIAAAASCAMAAADYBPgtAAAAGFBMVEUAAAAAAABpaWn/rAD/gQD8/wD/9AD/1QAKf4Y0AAAAAXRSTlMAQObYZgAAAMZJREFUeNqtlFEKhDAMRPPGrt7/xkvTpGKwsOLOx8yQyCsK1aoEsneiNeyZBChTQGvCpbrPrEHzY0d2a6K5BoPRFM+JsWBOKYg0F4loaIRFxsnBZds2zEYWM593hctzFrclQoASkYLZxGwrhKWI0+peJOE3BDfvKIJgHMdB5rRA5Fyo7vvAV0tEfou6DyFmM/Z9B+RZzBFRYurKNsw4Z6XdIIqJgfi40Cxnc0RvmRdDph4mXDqLre/IxXRzrx35XO8Q//rbfAHXcgiSVBcmkgAAAABJRU5ErkJggg=="

    static func image(_ base64: String) -> UIImage? {
        guard let data = Data(base64Encoded: base64) else { return nil }
        return UIImage(data: data, scale: 1)
    }
}

private struct Army2PadRepresentable: UIViewRepresentable {
    let onKey: (J2MEKey, Bool) -> Void

    func makeUIView(context: Context) -> Army2PadHostView {
        let view = Army2PadHostView()
        view.onKey = onKey
        return view
    }

    func updateUIView(_ uiView: Army2PadHostView, context: Context) {
        uiView.onKey = onKey
    }

    static func dismantleUIView(_ uiView: Army2PadHostView, coordinator: Void) {
        uiView.releaseAll()
        uiView.onKey = nil
    }
}

final class Army2PadHostView: UIView {
    private enum Action: Equatable {
        case key(J2MEKey)
    }

    private struct Cell {
        let action: Action
        let label: String?
        let symbol: String?
    }

    var onKey: ((J2MEKey, Bool) -> Void)?

    private let cells: [Cell] = [
        Cell(action: .key(.softLeft), label: Army2KeypadLabels.f1, symbol: nil),
        Cell(action: .key(.up), label: nil, symbol: "chevron.up"),
        // LUC MAX: ma phim rieng -40, client mo hop "Luc ban" (giong Menu > LUC MAX trong tran)
        Cell(action: .key(.lucMax), label: Army2KeypadLabels.lucMax, symbol: nil),
        Cell(action: .key(.softRight), label: Army2KeypadLabels.f2, symbol: nil),
        Cell(action: .key(.left), label: nil, symbol: "chevron.left"),
        Cell(action: .key(.down), label: nil, symbol: "chevron.down"),
        Cell(action: .key(.right), label: nil, symbol: "chevron.right"),
        Cell(action: .key(.fire), label: Army2KeypadLabels.ok, symbol: nil)
    ]
    private var cellViews: [UIView] = []
    private var labelViews: [UIImageView] = []
    private var labelImages: [UIImage?] = []
    private var touchCells: [ObjectIdentifier: Int] = [:]
    private var cellTouchCount: [Int] = []
    private var keyOwners: [J2MEKey: Int] = [:]
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    private let normalColor = UIColor.white
    private let pressedColor = UIColor(red: 0.80, green: 0.91, blue: 1.0, alpha: 1)
    private let lineColor = UIColor(red: 0.63, green: 0.81, blue: 0.93, alpha: 1)
    private let arrowColor = UIColor(red: 0.27, green: 0.30, blue: 0.36, alpha: 1)

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    private func setup() {
        backgroundColor = normalColor
        isMultipleTouchEnabled = true
        isExclusiveTouch = false
        isAccessibilityElement = false
        cellTouchCount = Array(repeating: 0, count: cells.count)
        for cell in cells {
            let box = UIView()
            box.isUserInteractionEnabled = false
            box.backgroundColor = normalColor
            box.layer.borderColor = lineColor.cgColor
            box.layer.borderWidth = 0.5
            let imageView = UIImageView()
            imageView.isUserInteractionEnabled = false
            var image: UIImage?
            if let label = cell.label {
                image = Army2KeypadLabels.image(label)
                imageView.image = image
                imageView.contentMode = .scaleToFill
                imageView.layer.magnificationFilter = .nearest
                imageView.layer.minificationFilter = .nearest
            } else if let symbol = cell.symbol {
                let configuration = UIImage.SymbolConfiguration(pointSize: 20, weight: .light)
                imageView.image = UIImage(systemName: symbol, withConfiguration: configuration)?
                    .withRenderingMode(.alwaysTemplate)
                imageView.tintColor = arrowColor
                imageView.contentMode = .center
            }
            box.addSubview(imageView)
            addSubview(box)
            cellViews.append(box)
            labelViews.append(imageView)
            labelImages.append(image)
        }
        haptic.prepare()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let columns = Army2KeypadView.columns
        let rows = Army2KeypadView.rows
        let cellWidth = bounds.width / CGFloat(columns)
        let cellHeight = bounds.height / CGFloat(rows)
        // cung 1 co chu cho moi nhan: theo chu dai nhat (LUC MAX), phong to
        // so nguyen lan diem anh that cua man hinh de chu pixel net
        let screenScale = max(window?.screen.scale ?? UIScreen.main.scale, 1)
        let widest = labelImages.compactMap { $0?.size.width }.max() ?? 1
        let devicePixels = max(1, floor(cellWidth * 0.8 * screenScale / widest))
        let labelScale = devicePixels / screenScale
        for (index, box) in cellViews.enumerated() {
            let column = index % columns
            let row = index / columns
            box.frame = CGRect(
                x: (CGFloat(column) * cellWidth).rounded(),
                y: (CGFloat(row) * cellHeight).rounded(),
                width: (CGFloat(column + 1) * cellWidth).rounded() - (CGFloat(column) * cellWidth).rounded(),
                height: (CGFloat(row + 1) * cellHeight).rounded() - (CGFloat(row) * cellHeight).rounded()
            )
            let imageView = labelViews[index]
            if let image = labelImages[index] {
                let size = CGSize(
                    width: image.size.width * labelScale,
                    height: image.size.height * labelScale
                )
                imageView.frame = CGRect(
                    x: ((box.bounds.width - size.width) / 2 * screenScale).rounded() / screenScale,
                    y: ((box.bounds.height - size.height) / 2 * screenScale).rounded() / screenScale,
                    width: size.width,
                    height: size.height
                )
            } else {
                imageView.frame = box.bounds
            }
        }
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            releaseAll()
        } else {
            setNeedsLayout()
        }
    }

    // ------------------------------------------------------------ cham
    private func cellIndex(at point: CGPoint) -> Int? {
        guard bounds.width > 0, bounds.height > 0,
              point.x >= 0, point.y >= 0,
              point.x < bounds.width, point.y < bounds.height else {
            return nil
        }
        let columns = Army2KeypadView.columns
        let rows = Army2KeypadView.rows
        let column = min(columns - 1, max(0, Int(point.x / (bounds.width / CGFloat(columns)))))
        let row = min(rows - 1, max(0, Int(point.y / (bounds.height / CGFloat(rows)))))
        return row * columns + column
    }

    private func isDirection(_ index: Int) -> Bool {
        switch cells[index].action {
        case .key(let key):
            return key == .up || key == .down || key == .left || key == .right
        }
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            guard let index = cellIndex(at: touch.location(in: self)) else { continue }
            touchCells[ObjectIdentifier(touch)] = index
            press(index)
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            let id = ObjectIdentifier(touch)
            guard let old = touchCells[id],
                  let now = cellIndex(at: touch.location(in: self)),
                  now != old,
                  isDirection(old),
                  isDirection(now) else {
                continue
            }
            release(old)
            touchCells[id] = now
            press(now)
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        finish(touches)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        finish(touches)
    }

    private func finish(_ touches: Set<UITouch>) {
        for touch in touches {
            if let index = touchCells.removeValue(forKey: ObjectIdentifier(touch)) {
                release(index)
            }
        }
    }

    func releaseAll() {
        touchCells.removeAll()
        for index in cellTouchCount.indices {
            cellTouchCount[index] = 0
            setPressed(index, false)
        }
        let held = Array(keyOwners.keys)
        keyOwners.removeAll()
        for key in held {
            onKey?(key, false)
        }
    }

    // ------------------------------------------------------------ phim
    private func press(_ index: Int) {
        cellTouchCount[index] += 1
        let first = cellTouchCount[index] == 1
        if first {
            setPressed(index, true)
            haptic.impactOccurred()
            haptic.prepare()
        }
        switch cells[index].action {
        case .key(let key):
            keyDown(key)
        }
    }

    private func release(_ index: Int) {
        guard cellTouchCount[index] > 0 else { return }
        cellTouchCount[index] -= 1
        if case .key(let key) = cells[index].action {
            keyUp(key)
        }
        if cellTouchCount[index] == 0 {
            setPressed(index, false)
        }
    }

    private func keyDown(_ key: J2MEKey) {
        let owners = keyOwners[key, default: 0]
        keyOwners[key] = owners + 1
        if owners == 0 {
            onKey?(key, true)
        }
    }

    private func keyUp(_ key: J2MEKey) {
        guard let owners = keyOwners[key] else { return }
        if owners <= 1 {
            keyOwners.removeValue(forKey: key)
            onKey?(key, false)
        } else {
            keyOwners[key] = owners - 1
        }
    }

    private func setPressed(_ index: Int, _ pressed: Bool) {
        guard index >= 0, index < cellViews.count else { return }
        cellViews[index].backgroundColor = pressed ? pressedColor : normalColor
    }
}
#endif

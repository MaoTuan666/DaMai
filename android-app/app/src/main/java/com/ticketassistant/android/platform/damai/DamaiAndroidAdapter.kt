package com.ticketassistant.android.platform.damai

import com.ticketassistant.android.accessibility.UiCheckedState
import com.ticketassistant.android.accessibility.UiNodeSnapshot
import com.ticketassistant.android.accessibility.UiSnapshot
import com.ticketassistant.android.accessibility.UiWindowSnapshot
import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TicketPlatform
import com.ticketassistant.android.domain.TicketTask
import com.ticketassistant.android.platform.api.ActionDecision
import com.ticketassistant.android.platform.api.AdapterStopReason
import com.ticketassistant.android.platform.api.ClickResolution
import com.ticketassistant.android.platform.api.ClickTarget
import com.ticketassistant.android.platform.api.EvidenceKind
import com.ticketassistant.android.platform.api.NodeReference
import com.ticketassistant.android.platform.api.PageEvidence
import com.ticketassistant.android.platform.api.PageFingerprint
import com.ticketassistant.android.platform.api.PageResult
import com.ticketassistant.android.platform.api.PageUnknownReason
import com.ticketassistant.android.platform.api.PlatformAdapter
import com.ticketassistant.android.platform.api.PlatformPageType
import com.ticketassistant.android.platform.api.TargetField
import com.ticketassistant.android.platform.api.WaitReason
import com.ticketassistant.android.runtime.AutomationPhase
import com.ticketassistant.android.runtime.EngineRuntimeState
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

object DamaiPageTypes {
    val WAIT_SALE_DETAIL = PlatformPageType("DM_WAIT_SALE_DETAIL")
    val BOOKABLE_PROJECT_DETAIL = PlatformPageType("DM_BOOKABLE_PROJECT_DETAIL")
    val RETURN_REENTER_DETAIL = PlatformPageType("DM_RETURN_REENTER_DETAIL")
    val SEAT_ENTRY = PlatformPageType("DM_SEAT_ENTRY")
    val ORDER_CONFIRM = PlatformPageType("DM_ORDER_CONFIRM")
    val SUBMIT_RESULT_DIALOG = PlatformPageType("DM_SUBMIT_RESULT_DIALOG")
    val RETURN_SOLD_OUT = PlatformPageType("DM_RETURN_SOLD_OUT")
    val RETURN_TIER_SELECTION = PlatformPageType("DM_RETURN_TIER_SELECTION")
    val RETURN_CONFIRM = PlatformPageType("DM_RETURN_CONFIRM")
    val CHECKOUT = PlatformPageType("DM_CHECKOUT")
}

object DamaiEventCodes {
    const val WAIT_RESERVED = "DM_WAIT_RESERVED"
    const val CLICK_BOOK_NOW = "DM_CLICK_BOOK_NOW"
    const val CLICK_SEAT_ENTRY = "DM_CLICK_SEAT_ENTRY"
    const val CLICK_SUBMIT = "DM_CLICK_SUBMIT"
    const val POPUP_CONTINUE = "DM_POPUP_CONTINUE"
    const val POPUP_RETURN = "DM_POPUP_RETURN"
    const val SWITCH_TO_RETURN = "DM_SWITCH_TO_RETURN"
    const val RETURN_BACK_TO_DETAIL = "DM_RETURN_BACK_TO_DETAIL"
    const val RETURN_REENTER = "DM_RETURN_REENTER"
    const val SELECT_TARGET_TIER = "DM_SELECT_TARGET_TIER"
    const val TARGET_TIER_AVAILABLE = "DM_TARGET_TIER_AVAILABLE"
    const val CONFIRM_TARGET_TIER = "DM_CONFIRM_TARGET_TIER"
    const val ORDER_LOCKED = "DM_ORDER_LOCKED"
    const val SAFE_PAUSE = "DM_SAFE_PAUSE"
}

class DamaiAndroidAdapter : PlatformAdapter {
    override val platform = TicketPlatform.DAMAI
    override val supportedPackages: Set<String> = setOf(DAMAI_PACKAGE)
    override val supportedModes: Set<RunMode> = RunMode.entries.toSet()

    override fun detectPage(
        snapshot: UiSnapshot,
        task: TicketTask,
    ): PageResult {
        if (snapshot.packageName !in supportedPackages) {
            return unknown(PageUnknownReason.INSUFFICIENT_EVIDENCE)
        }
        val index = DamaiSnapshotIndex(snapshot) ?: return unknown(
            PageUnknownReason.INSUFFICIENT_EVIDENCE,
        )

        detectProtectedPage(index)?.let { return it }
        detectCheckout(index)?.let { return it }
        detectSubmitDialog(index)?.let { return it }

        if (index.hasUnderlyingPlatformWindow) {
            return unknown(PageUnknownReason.UNKNOWN_TOP_WINDOW)
        }

        detectOrderConfirmation(index, task)?.let { return it }
        detectSeatEntry(index, task)?.let { return it }
        detectReturnSelection(index, task)?.let { return it }
        detectProjectDetail(index, task)?.let { return it }

        return unknown(PageUnknownReason.INSUFFICIENT_EVIDENCE)
    }

    override fun decideAction(
        page: PageResult.Recognized,
        task: TicketTask,
        runtime: EngineRuntimeState,
    ): ActionDecision = when (page.pageType) {
        DamaiPageTypes.WAIT_SALE_DETAIL -> ActionDecision.Wait(
            reason = WaitReason.SALE_NOT_OPEN,
            eventCode = DamaiEventCodes.WAIT_RESERVED,
        )

        DamaiPageTypes.BOOKABLE_PROJECT_DETAIL -> click(
            page = page,
            evidenceCode = EVIDENCE_BOOK_NOW,
            eventCode = if (runtime.phase == AutomationPhase.RETURN_MONITOR) {
                DamaiEventCodes.RETURN_REENTER
            } else {
                DamaiEventCodes.CLICK_BOOK_NOW
            },
        )

        DamaiPageTypes.RETURN_REENTER_DETAIL -> returnPhaseDecision(task, runtime) {
            click(page, EVIDENCE_BOOK_NOW, DamaiEventCodes.RETURN_REENTER)
        }

        DamaiPageTypes.SEAT_ENTRY -> click(
            page,
            EVIDENCE_SEAT_ENTRY,
            DamaiEventCodes.CLICK_SEAT_ENTRY,
        )

        DamaiPageTypes.ORDER_CONFIRM -> click(
            page = page,
            evidenceCode = EVIDENCE_SUBMIT,
            eventCode = DamaiEventCodes.CLICK_SUBMIT,
        )

        DamaiPageTypes.SUBMIT_RESULT_DIALOG -> decideSubmitDialog(page, task, runtime)

        DamaiPageTypes.RETURN_SOLD_OUT -> returnPhaseDecision(task, runtime) {
            val back = page.evidenceNode(EVIDENCE_BACK)
            if (back != null) {
                click(page, EVIDENCE_BACK, DamaiEventCodes.RETURN_BACK_TO_DETAIL)
            } else {
                ActionDecision.GlobalBack(
                    expectedPageFingerprint = page.fingerprint,
                    eventCode = DamaiEventCodes.RETURN_BACK_TO_DETAIL,
                )
            }
        }

        DamaiPageTypes.RETURN_TIER_SELECTION -> returnPhaseDecision(task, runtime) {
            click(page, EVIDENCE_PRICE, DamaiEventCodes.SELECT_TARGET_TIER)
        }

        DamaiPageTypes.RETURN_CONFIRM -> returnPhaseDecision(task, runtime) {
            click(page, EVIDENCE_CONFIRM, DamaiEventCodes.CONFIRM_TARGET_TIER)
        }

        DamaiPageTypes.CHECKOUT -> ActionDecision.Stop(
            reason = AdapterStopReason.ORDER_LOCKED,
            eventCode = DamaiEventCodes.ORDER_LOCKED,
        )

        else -> ActionDecision.Wait(WaitReason.PAGE_NOT_ACTIONABLE)
    }

    private fun detectProtectedPage(index: DamaiSnapshotIndex): PageResult.Unknown? {
        val reason = when {
            index.containsAny("验证码", "安全验证", "滑动验证") ->
                PageUnknownReason.VERIFICATION_REQUIRED

            index.containsAny("风险提示", "风控验证", "操作过于频繁") ->
                PageUnknownReason.RISK_CONTROL_REQUIRED

            index.containsAny("设备校验", "设备验证") ->
                PageUnknownReason.DEVICE_CHECK_REQUIRED

            index.containsAny("请先登录", "登录后继续") ->
                PageUnknownReason.LOGIN_REQUIRED

            index.containsAny("权限申请", "请开启权限") ->
                PageUnknownReason.PERMISSION_REQUIRED

            else -> null
        }
        return reason?.let(::unknown)
    }

    private fun detectCheckout(index: DamaiSnapshotIndex): PageResult.Recognized? {
        val amount = index.findContaining("订单金额") ?: return null
        val paymentMethod = index.findContaining("支付方式") ?: return null
        val pay = index.findExact("付款") ?: return null
        return index.recognized(
            pageType = DamaiPageTypes.CHECKOUT,
            evidence = listOf(
                amount.evidence(EVIDENCE_PAYMENT_AMOUNT),
                paymentMethod.evidence(EVIDENCE_PAYMENT_METHOD),
                pay.evidence(EVIDENCE_PAY),
            ),
        )
    }

    private fun detectSubmitDialog(index: DamaiSnapshotIndex): PageResult.Recognized? {
        val continueButton = index.findExact("继续尝试")?.takeIf(index::isActionable)
        val returnButton = index.findExact("返回重新选购")?.takeIf(index::isActionable)
        if (continueButton == null && returnButton == null) return null

        val evidence = buildList {
            continueButton?.let { add(it.evidence(EVIDENCE_POPUP_CONTINUE)) }
            returnButton?.let { add(it.evidence(EVIDENCE_POPUP_RETURN)) }
        }
        return index.recognized(
            pageType = DamaiPageTypes.SUBMIT_RESULT_DIALOG,
            evidence = evidence,
        )
    }

    private fun detectOrderConfirmation(
        index: DamaiSnapshotIndex,
        task: TicketTask,
    ): PageResult? {
        val confirmation = index.findContaining("确认购买") ?: return null
        val attendeeSection = index.findContaining("实名观演人") ?: return null
        val submit = index.findExact("立即提交") ?: return null

        val date = index.findDate(task.targetDate)
            ?: return mismatch(TargetField.DATE, task.targetDate)
        val price = index.findPrice(task.targetPriceFen)
            ?: return mismatch(TargetField.PRICE, task.targetPriceFen.toString())
        if (!index.isActionable(submit)) {
            return unknown(PageUnknownReason.INSUFFICIENT_EVIDENCE)
        }

        return index.recognized(
            pageType = DamaiPageTypes.ORDER_CONFIRM,
            evidence = listOf(
                confirmation.evidence(EVIDENCE_CONFIRM_PURCHASE),
                date.evidence(EVIDENCE_DATE),
                price.evidence(EVIDENCE_PRICE),
                attendeeSection.evidence(EVIDENCE_ATTENDEE_SECTION),
                submit.evidence(EVIDENCE_SUBMIT),
            ),
        )
    }

    private fun detectSeatEntry(
        index: DamaiSnapshotIndex,
        task: TicketTask,
    ): PageResult? {
        val seatEntry = index.findExact("去选座") ?: return null
        val date = index.findDate(task.targetDate)
            ?: return mismatch(TargetField.DATE, task.targetDate)
        val price = index.findPrice(task.targetPriceFen)
            ?: return mismatch(TargetField.PRICE, task.targetPriceFen.toString())
        if (!index.isActionable(seatEntry)) {
            return unknown(PageUnknownReason.INSUFFICIENT_EVIDENCE)
        }
        return index.recognized(
            pageType = DamaiPageTypes.SEAT_ENTRY,
            evidence = listOf(
                date.evidence(EVIDENCE_DATE),
                price.evidence(EVIDENCE_PRICE),
                seatEntry.evidence(EVIDENCE_SEAT_ENTRY),
            ),
        )
    }

    private fun detectReturnSelection(
        index: DamaiSnapshotIndex,
        task: TicketTask,
    ): PageResult? {
        val sessionLabel = index.findExact("场次") ?: return null
        val tierLabel = index.findExact("票档") ?: return null
        val confirm = index.findExact("确定") ?: return null

        val date = index.findDate(task.targetDate)
            ?: return mismatch(TargetField.DATE, task.targetDate)
        val price = index.findPrice(task.targetPriceFen)
            ?: return mismatch(TargetField.PRICE, task.targetPriceFen.toString())
        val soldOut = index.findExact("缺货登记")
        val commonEvidence = listOf(
            sessionLabel.evidence(EVIDENCE_SESSION_LABEL),
            date.evidence(EVIDENCE_DATE),
            tierLabel.evidence(EVIDENCE_TIER_LABEL),
            price.evidence(EVIDENCE_PRICE),
            confirm.evidence(EVIDENCE_CONFIRM),
        )

        if (
            soldOut != null &&
            index.areStructurallyRelated(price, soldOut) &&
            !index.isActionable(confirm)
        ) {
            val back = index.findExact("返回")
            return index.recognized(
                pageType = DamaiPageTypes.RETURN_SOLD_OUT,
                evidence = buildList {
                    addAll(commonEvidence)
                    add(soldOut.evidence(EVIDENCE_SOLD_OUT))
                    if (back != null && index.isActionable(back)) {
                        add(back.evidence(EVIDENCE_BACK))
                    }
                },
                validStartPhases = setOf(AutomationPhase.RETURN_MONITOR),
            )
        }

        if (!index.isActionable(price)) {
            return unknown(PageUnknownReason.INSUFFICIENT_EVIDENCE)
        }
        val tierSelected = index.isSelected(price)
        val confirmActionable = index.isActionable(confirm)
        if (!tierSelected && !confirmActionable) {
            return index.recognized(
                pageType = DamaiPageTypes.RETURN_TIER_SELECTION,
                evidence = commonEvidence,
                validStartPhases = setOf(AutomationPhase.RETURN_MONITOR),
                observationEventCode = DamaiEventCodes.TARGET_TIER_AVAILABLE,
            )
        }
        if (tierSelected && confirmActionable) {
            return index.recognized(
                pageType = DamaiPageTypes.RETURN_CONFIRM,
                evidence = commonEvidence,
                validStartPhases = setOf(AutomationPhase.RETURN_MONITOR),
                observationEventCode = DamaiEventCodes.TARGET_TIER_AVAILABLE,
            )
        }
        return unknown(PageUnknownReason.INSUFFICIENT_EVIDENCE)
    }

    private fun detectProjectDetail(
        index: DamaiSnapshotIndex,
        task: TicketTask,
    ): PageResult? {
        val reserved = index.findExact("已预约")
        val bookNow = index.findExact("立即预订")
        if (reserved == null && bookNow == null) return null

        val date = index.findDate(task.targetDate)
            ?: return mismatch(TargetField.DATE, task.targetDate)
        val guide = index.findContaining("抢票攻略")

        if (reserved != null) {
            if (guide == null) return unknown(PageUnknownReason.INSUFFICIENT_EVIDENCE)
            return index.recognized(
                pageType = DamaiPageTypes.WAIT_SALE_DETAIL,
                evidence = listOf(
                    date.evidence(EVIDENCE_DATE),
                    guide.evidence(EVIDENCE_GUIDE),
                    reserved.evidence(EVIDENCE_RESERVED),
                ),
                validStartPhases = setOf(AutomationPhase.SALE),
            )
        }

        val actionableBookNow = requireNotNull(bookNow)
        if (!index.isActionable(actionableBookNow)) {
            return unknown(PageUnknownReason.INSUFFICIENT_EVIDENCE)
        }
        return index.recognized(
            pageType = if (guide == null) {
                DamaiPageTypes.RETURN_REENTER_DETAIL
            } else {
                DamaiPageTypes.BOOKABLE_PROJECT_DETAIL
            },
            evidence = buildList {
                add(date.evidence(EVIDENCE_DATE))
                guide?.let { add(it.evidence(EVIDENCE_GUIDE)) }
                add(actionableBookNow.evidence(EVIDENCE_BOOK_NOW))
            },
            validStartPhases = if (guide == null) {
                setOf(AutomationPhase.RETURN_MONITOR)
            } else {
                setOf(AutomationPhase.SALE, AutomationPhase.RETURN_MONITOR)
            },
        )
    }

    private fun decideSubmitDialog(
        page: PageResult.Recognized,
        task: TicketTask,
        runtime: EngineRuntimeState,
    ): ActionDecision {
        if (page.evidenceNode(EVIDENCE_POPUP_CONTINUE) != null) {
            return click(
                page = page,
                evidenceCode = EVIDENCE_POPUP_CONTINUE,
                eventCode = DamaiEventCodes.POPUP_CONTINUE,
            )
        }
        if (
            task.runMode == RunMode.SALE_ONLY &&
            runtime.phase == AutomationPhase.SALE
        ) {
            return ActionDecision.Stop(
                reason = AdapterStopReason.SALE_FLOW_EXHAUSTED,
                eventCode = DamaiEventCodes.POPUP_RETURN,
            )
        }
        return click(page, EVIDENCE_POPUP_RETURN, DamaiEventCodes.POPUP_RETURN)
    }

    private fun returnPhaseDecision(
        task: TicketTask,
        runtime: EngineRuntimeState,
        decision: () -> ActionDecision,
    ): ActionDecision {
        if (runtime.phase == AutomationPhase.RETURN_MONITOR) return decision()
        return if (task.runMode == RunMode.SALE_THEN_RETURN) {
            ActionDecision.SwitchPhase(
                phase = AutomationPhase.RETURN_MONITOR,
                eventCode = DamaiEventCodes.SWITCH_TO_RETURN,
            )
        } else {
            ActionDecision.Stop(
                reason = AdapterStopReason.SALE_FLOW_EXHAUSTED,
                eventCode = DamaiEventCodes.POPUP_RETURN,
            )
        }
    }

    private fun click(
        page: PageResult.Recognized,
        evidenceCode: String,
        eventCode: String,
    ): ActionDecision.Click {
        val node = requireNotNull(page.evidenceNode(evidenceCode))
        return ActionDecision.Click(
            target = ClickTarget(
                node = node,
                expectedPackageName = DAMAI_PACKAGE,
                expectedPageFingerprint = page.fingerprint,
                resolution = ClickResolution.NEAREST_CLICKABLE_ANCESTOR,
                maximumAncestorDepth = MAX_CLICKABLE_PARENT_DEPTH,
            ),
            eventCode = eventCode,
        )
    }

    private fun PageResult.Recognized.evidenceNode(code: String): NodeReference? =
        evidence.firstOrNull { it.evidenceCode == code }?.node

    private fun mismatch(
        field: TargetField,
        expected: String,
    ): PageResult.Mismatch = PageResult.Mismatch(
        field = field,
        expected = expected,
        actual = null,
        eventCode = DamaiEventCodes.SAFE_PAUSE,
    )

    private fun unknown(reason: PageUnknownReason): PageResult.Unknown =
        PageResult.Unknown(
            reason = reason,
            eventCode = DamaiEventCodes.SAFE_PAUSE,
        )

    companion object {
        const val DAMAI_PACKAGE = "cn.damai"
        private const val MAX_CLICKABLE_PARENT_DEPTH = 4

        private const val EVIDENCE_GUIDE = "DM_GUIDE"
        private const val EVIDENCE_RESERVED = "DM_RESERVED"
        private const val EVIDENCE_BOOK_NOW = "DM_BOOK_NOW"
        private const val EVIDENCE_DATE = "DM_TARGET_DATE"
        private const val EVIDENCE_PRICE = "DM_TARGET_PRICE"
        private const val EVIDENCE_ATTENDEE_SECTION = "DM_ATTENDEE_SECTION"
        private const val EVIDENCE_SEAT_ENTRY = "DM_SEAT_ENTRY"
        private const val EVIDENCE_CONFIRM_PURCHASE = "DM_CONFIRM_PURCHASE"
        private const val EVIDENCE_SUBMIT = "DM_SUBMIT"
        private const val EVIDENCE_POPUP_CONTINUE = "DM_POPUP_CONTINUE"
        private const val EVIDENCE_POPUP_RETURN = "DM_POPUP_RETURN"
        private const val EVIDENCE_SESSION_LABEL = "DM_SESSION_LABEL"
        private const val EVIDENCE_TIER_LABEL = "DM_TIER_LABEL"
        private const val EVIDENCE_SOLD_OUT = "DM_SOLD_OUT"
        private const val EVIDENCE_CONFIRM = "DM_CONFIRM"
        private const val EVIDENCE_BACK = "DM_BACK"
        private const val EVIDENCE_PAYMENT_AMOUNT = "DM_PAYMENT_AMOUNT"
        private const val EVIDENCE_PAYMENT_METHOD = "DM_PAYMENT_METHOD"
        private const val EVIDENCE_PAY = "DM_PAY"
    }
}

private class DamaiSnapshotIndex private constructor(
    private val snapshot: UiSnapshot,
    val topWindow: UiWindowSnapshot,
    private val nodes: List<IndexedNode>,
    val hasUnderlyingPlatformWindow: Boolean,
) {
    fun findExact(value: String): IndexedNode? {
        val expected = normalize(value)
        return nodes.firstOrNull { node -> node.strings.any { it == expected } }
    }

    fun findContaining(value: String): IndexedNode? {
        val expected = compact(value)
        if (expected.isEmpty()) return null
        return nodes.firstOrNull { node ->
            node.strings.any { expected in compact(it) }
        }
    }

    fun containsAny(vararg values: String): Boolean =
        values.any { findContaining(it) != null }

    fun findPrice(priceFen: Long): IndexedNode? {
        val matches = findAllMatching(pricePattern(priceFen)).toList()
        return matches.firstOrNull(::isActionable) ?: matches.firstOrNull()
    }

    fun findDate(value: String): IndexedNode? {
        val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return null
        val year = date.year
        val month = date.monthValue
        val day = date.dayOfMonth
        val paddedMonth = month.toString().padStart(2, '0')
        val paddedDay = day.toString().padStart(2, '0')
        val fullDatePattern = Regex(
            """(?:""" +
                """(?<![\d./-])$year\s*[-./]\s*0?$month\s*[-./]\s*0?$day(?![\d./-])|""" +
                """(?<!\d)$year\s*年\s*0?$month\s*月\s*0?$day""" +
                """(?:\s*日(?!\d)|(?![\d日])))""",
        )
        val monthDayPattern = Regex(
            """(?:""" +
                """(?<!\d)$paddedMonth\s*[-./]\s*$paddedDay(?![\d./-])|""" +
                """(?<!\d)0?$month\s*月\s*0?$day""" +
                """(?:\s*日(?!\d)|(?![\d日])))""",
        )
        return findFirstMatching(fullDatePattern)
            ?: findStandaloneMonthDay(monthDayPattern, expectedYear = year)
    }

    fun isActionable(node: IndexedNode): Boolean =
        nodeAndAncestors(node, MAX_LOOKUP_ANCESTOR_DEPTH)
            .any { candidate ->
                candidate.node.packageName == snapshot.packageName &&
                    candidate.node.isVisibleToUser &&
                    candidate.node.isEnabled &&
                    candidate.node.isClickable &&
                    candidate.node.boundsInScreen.right > candidate.node.boundsInScreen.left &&
                    candidate.node.boundsInScreen.bottom > candidate.node.boundsInScreen.top
            }

    fun isSelected(node: IndexedNode): Boolean =
        nodeAndAncestors(node, MAX_SELECTION_ANCESTOR_DEPTH).any {
            it.node.isSelected ||
                it.node.checkedState == UiCheckedState.CHECKED ||
                it.node.checkedState == UiCheckedState.PARTIAL
        }

    fun areStructurallyRelated(
        first: IndexedNode,
        second: IndexedNode,
    ): Boolean {
        if (first.reference.windowId != second.reference.windowId) return false
        val commonDepth = first.reference.path
            .zip(second.reference.path)
            .takeWhile { (left, right) -> left == right }
            .size
        return first.reference.path.size - commonDepth <= MAX_RELATED_NODE_DISTANCE &&
            second.reference.path.size - commonDepth <= MAX_RELATED_NODE_DISTANCE
    }

    fun recognized(
        pageType: PlatformPageType,
        evidence: List<PageEvidence>,
        validStartPhases: Set<AutomationPhase> = emptySet(),
        observationEventCode: String? = null,
    ): PageResult.Recognized = PageResult.Recognized(
        pageType = pageType,
        fingerprint = fingerprint(pageType, evidence),
        windowId = topWindow.id,
        evidence = evidence,
        validStartPhases = validStartPhases,
        observationEventCode = observationEventCode,
    )

    private fun findFirstMatching(pattern: Regex): IndexedNode? =
        findAllMatching(pattern).firstOrNull()

    private fun findStandaloneMonthDay(
        pattern: Regex,
        expectedYear: Int,
    ): IndexedNode? = nodes.firstOrNull { node ->
        node.strings.any { value ->
            pattern.findAll(value).any { match ->
                val prefix = value.substring(0, match.range.first)
                !YEAR_PREFIX_PATTERN.containsMatchIn(prefix) &&
                    !containsConflictingRelatedYear(node, expectedYear)
            }
        }
    }

    private fun containsConflictingRelatedYear(
        dateNode: IndexedNode,
        expectedYear: Int,
    ): Boolean = nodes
        .asSequence()
        .filter { candidate -> areDateFragmentsRelated(dateNode, candidate) }
        .any { node ->
            node.strings.any { value ->
                DATE_YEAR_PATTERNS.any { pattern ->
                    pattern.findAll(value).any { match ->
                        match.groupValues[1]
                            .toIntOrNull()
                            ?.let { it != expectedYear } == true
                    }
                }
            }
        }

    private fun areDateFragmentsRelated(
        first: IndexedNode,
        second: IndexedNode,
    ): Boolean {
        if (first.reference.windowId != second.reference.windowId) return false
        val firstPath = first.reference.path
        val secondPath = second.reference.path
        if (firstPath == secondPath) return true
        if (
            firstPath.size < secondPath.size &&
            secondPath.take(firstPath.size) == firstPath
        ) {
            return true
        }
        if (
            secondPath.size < firstPath.size &&
            firstPath.take(secondPath.size) == secondPath
        ) {
            return true
        }
        return firstPath.isNotEmpty() &&
            firstPath.size == secondPath.size &&
            firstPath.dropLast(1) == secondPath.dropLast(1) &&
            abs(firstPath.last() - secondPath.last()) <= MAX_DATE_FRAGMENT_SIBLING_GAP
    }

    private fun findAllMatching(pattern: Regex): Sequence<IndexedNode> =
        nodes.asSequence().filter { node ->
            node.strings.any { pattern.containsMatchIn(it) }
        }

    private fun pricePattern(priceFen: Long): Regex {
        val whole = priceFen / 100
        val fraction = priceFen % 100
        val decimal = if (fraction == 0L) {
            "$whole(?:\\.00)?"
        } else {
            "$whole\\.${fraction.toString().padStart(2, '0')}"
        }
        return Regex("(?:[¥￥]\\s*$decimal|$decimal\\s*元)")
    }

    private fun nodeAndAncestors(
        indexedNode: IndexedNode,
        maximumDepth: Int,
    ): Sequence<IndexedNode> = sequence {
        for (depth in 0..minOf(maximumDepth, indexedNode.reference.path.size)) {
            val path = indexedNode.reference.path.dropLast(depth)
            nodes.firstOrNull { it.reference.path == path }?.let { yield(it) }
        }
    }

    private fun fingerprint(
        pageType: PlatformPageType,
        evidence: List<PageEvidence>,
    ): PageFingerprint {
        val digestInput = buildString {
            append(pageType.code)
            evidence.sortedBy(PageEvidence::evidenceCode).forEach { item ->
                val node = nodes.first { it.reference == item.node }
                append('|')
                append(item.evidenceCode)
                append(':')
                append(item.node.path.joinToString("."))
                append(':')
                append(node.strings.joinToString("~"))
                append(':')
                append(node.node.isEnabled)
                append(':')
                append(node.node.isClickable)
                append(':')
                append(node.node.isSelected)
                append(':')
                append(node.node.checkedState.name)
            }
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(digestInput.toByteArray(Charsets.UTF_8))
        val value = bytes.joinToString("") { "%02x".format(Locale.ROOT, it) }
        return PageFingerprint(value)
    }

    companion object {
        private const val MAX_LOOKUP_ANCESTOR_DEPTH = 4
        private const val MAX_SELECTION_ANCESTOR_DEPTH = 3
        private const val MAX_RELATED_NODE_DISTANCE = 2
        private const val MAX_DATE_FRAGMENT_SIBLING_GAP = 2
        private val YEAR_PREFIX_PATTERN = Regex("""\d+\s*(?:年|[-./])\s*$""")
        private val DATE_YEAR_PATTERNS = listOf(
            Regex("""(?<!\d)(\d{4})\s*年\s*\d{1,2}\s*月\s*\d{1,2}(?:\s*日)?(?!\d)"""),
            Regex(
                """(?<![\d./-])(\d{4})\s*[-./]\s*\d{1,2}\s*[-./]\s*""" +
                    """\d{1,2}(?![\d./-])""",
            ),
            Regex("""^(\d{4})\s*年?$"""),
        )

        operator fun invoke(snapshot: UiSnapshot): DamaiSnapshotIndex? {
            val platformWindows = snapshot.windows
                .filter { it.root?.packageName == snapshot.packageName }
                .sortedByDescending(UiWindowSnapshot::layer)
            val topWindow = platformWindows.firstOrNull() ?: return null
            val root = topWindow.root ?: return null
            return DamaiSnapshotIndex(
                snapshot = snapshot,
                topWindow = topWindow,
                nodes = flatten(topWindow.id, root),
                hasUnderlyingPlatformWindow = platformWindows.size > 1,
            )
        }

        private fun flatten(
            windowId: Int,
            root: UiNodeSnapshot,
        ): List<IndexedNode> = buildList {
            fun visit(node: UiNodeSnapshot) {
                if (node.isVisibleToUser) {
                    add(
                        IndexedNode(
                            reference = NodeReference(windowId, node.path),
                            node = node,
                            strings = listOfNotNull(node.text, node.contentDescription)
                                .map(::normalize)
                                .filter(String::isNotEmpty),
                        ),
                    )
                }
                node.children.forEach(::visit)
            }
            visit(root)
        }

        private fun normalize(value: String): String =
            value.trim().replace(Regex("\\s+"), " ")

        private fun compact(value: String): String =
            normalize(value).replace(" ", "")
    }
}

private data class IndexedNode(
    val reference: NodeReference,
    val node: UiNodeSnapshot,
    val strings: List<String>,
) {
    fun evidence(
        code: String,
        kind: EvidenceKind = EvidenceKind.TEXT_AND_ROLE,
    ): PageEvidence = PageEvidence(
        node = reference,
        kind = kind,
        evidenceCode = code,
    )
}

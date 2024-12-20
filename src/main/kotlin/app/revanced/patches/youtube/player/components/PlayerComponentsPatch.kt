package app.revanced.patches.youtube.player.components

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.shared.fingerprints.StartVideoInformerFingerprint
import app.revanced.patches.shared.litho.LithoFilterPatch
import app.revanced.patches.shared.spans.InclusiveSpanPatch
import app.revanced.patches.youtube.player.components.fingerprints.CrowdfundingBoxFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.FilmStripOverlayConfigFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.FilmStripOverlayInteractionFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.FilmStripOverlayParentFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.FilmStripOverlayPreviewFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.InfoCardsIncognitoFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.LayoutCircleFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.LayoutIconFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.LayoutVideoFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.LithoComponentOnClickListenerFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.NoticeOnClickListenerFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.OfflineActionsOnClickListenerFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.QuickSeekOverlayFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.SeekEduContainerFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.SuggestedActionsFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.TouchAreaOnClickListenerFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.VideoZoomSnapIndicatorFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.WatermarkFingerprint
import app.revanced.patches.youtube.player.components.fingerprints.WatermarkParentFingerprint
import app.revanced.patches.youtube.player.speedoverlay.SpeedOverlayPatch
import app.revanced.patches.youtube.utils.compatibility.Constants.COMPATIBLE_PACKAGE
import app.revanced.patches.youtube.utils.controlsoverlay.ControlsOverlayConfigPatch
import app.revanced.patches.youtube.utils.fingerprints.EngagementPanelBuilderFingerprint
import app.revanced.patches.youtube.utils.fingerprints.YouTubeControlsOverlayFingerprint
import app.revanced.patches.youtube.utils.fix.suggestedvideoendscreen.SuggestedVideoEndScreenPatch
import app.revanced.patches.youtube.utils.integrations.Constants.COMPONENTS_PATH
import app.revanced.patches.youtube.utils.integrations.Constants.PLAYER_CLASS_DESCRIPTOR
import app.revanced.patches.youtube.utils.integrations.Constants.SPANS_PATH
import app.revanced.patches.youtube.utils.playertype.PlayerTypeHookPatch
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.DarkBackground
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.FadeDurationFast
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.ScrimOverlay
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.SeekUndoEduOverlayStub
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.TapBloomView
import app.revanced.patches.youtube.utils.settings.SettingsPatch
import app.revanced.patches.youtube.video.information.VideoInformationPatch
import app.revanced.util.REGISTER_TEMPLATE_REPLACEMENT
import app.revanced.util.findMethodOrThrow
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstruction
import app.revanced.util.indexOfFirstInstructionOrThrow
import app.revanced.util.indexOfFirstInstructionReversedOrThrow
import app.revanced.util.indexOfFirstWideLiteralInstructionValueOrThrow
import app.revanced.util.injectLiteralInstructionViewCall
import app.revanced.util.patch.BaseBytecodePatch
import app.revanced.util.resultOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
object PlayerComponentsPatch : BaseBytecodePatch(
    name = "Player components",
    description = "Adds options to hide or change components related to the video player.",
    dependencies = setOf(
        ControlsOverlayConfigPatch::class,
        InclusiveSpanPatch::class,
        LithoFilterPatch::class,
        PlayerTypeHookPatch::class,
        SettingsPatch::class,
        SharedResourceIdPatch::class,
        SpeedOverlayPatch::class,
        SuggestedVideoEndScreenPatch::class,
        VideoInformationPatch::class
    ),
    compatiblePackages = COMPATIBLE_PACKAGE,
    fingerprints = setOf(
        CrowdfundingBoxFingerprint,
        EngagementPanelBuilderFingerprint,
        FilmStripOverlayParentFingerprint,
        InfoCardsIncognitoFingerprint,
        LayoutCircleFingerprint,
        LayoutIconFingerprint,
        LayoutVideoFingerprint,
        LithoComponentOnClickListenerFingerprint,
        NoticeOnClickListenerFingerprint,
        OfflineActionsOnClickListenerFingerprint,
        QuickSeekOverlayFingerprint,
        SeekEduContainerFingerprint,
        StartVideoInformerFingerprint,
        SuggestedActionsFingerprint,
        TouchAreaOnClickListenerFingerprint,
        VideoZoomSnapIndicatorFingerprint,
        WatermarkParentFingerprint,
        YouTubeControlsOverlayFingerprint,
    )
) {
    private const val PLAYER_COMPONENTS_FILTER_CLASS_DESCRIPTOR =
        "$COMPONENTS_PATH/PlayerComponentsFilter;"
    private const val SANITIZE_VIDEO_SUBTITLE_FILTER_CLASS_DESCRIPTOR =
        "$SPANS_PATH/SanitizeVideoSubtitleFilter;"

    override fun execute(context: BytecodeContext) {

        // region patch for custom player overlay opacity

        YouTubeControlsOverlayFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val constIndex = indexOfFirstWideLiteralInstructionValueOrThrow(ScrimOverlay)
                val targetIndex = indexOfFirstInstructionOrThrow(constIndex, Opcode.CHECK_CAST)
                val targetParameter = getInstruction<ReferenceInstruction>(targetIndex).reference
                val targetRegister = getInstruction<OneRegisterInstruction>(targetIndex).registerA

                if (!targetParameter.toString().endsWith("Landroid/widget/ImageView;"))
                    throw PatchException("Method signature parameter did not match: $targetParameter")

                addInstruction(
                    targetIndex + 1,
                    "invoke-static {v$targetRegister}, $PLAYER_CLASS_DESCRIPTOR->changeOpacity(Landroid/widget/ImageView;)V"
                )
            }
        }

        // endregion

        // region patch for disable auto player popup panels

        fun MutableMethod.hookInitVideoPanel(initVideoPanel: Int) =
            addInstructions(
                0, """
                    const/4 v0, $initVideoPanel
                    invoke-static {v0}, $PLAYER_CLASS_DESCRIPTOR->setInitVideoPanel(Z)V
                    """
            )

        arrayOf(
            LithoComponentOnClickListenerFingerprint,
            NoticeOnClickListenerFingerprint,
            OfflineActionsOnClickListenerFingerprint,
            StartVideoInformerFingerprint,
        ).forEach { fingerprint ->
            fingerprint.resultOrThrow().mutableMethod.apply {
                if (fingerprint == StartVideoInformerFingerprint) {
                    hookInitVideoPanel(1)
                } else {
                    val syntheticIndex =
                        indexOfFirstInstruction(0, Opcode.NEW_INSTANCE)
                    if (syntheticIndex >= 0) {
                        val syntheticReference =
                            getInstruction<ReferenceInstruction>(syntheticIndex).reference.toString()

                        context.findMethodOrThrow(syntheticReference) {
                            name == "onClick"
                        }.hookInitVideoPanel(0)
                    }
                }
            }
        }

        EngagementPanelBuilderFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                addInstructionsWithLabels(
                    0, """
                        move/from16 v0, p4
                        invoke-static {v0}, $PLAYER_CLASS_DESCRIPTOR->disableAutoPlayerPopupPanels(Z)Z
                        move-result v0
                        if-eqz v0, :shown
                        const/4 v0, 0x0
                        return-object v0
                        """, ExternalLabel("shown", getInstruction(0))
                )
            }
        }

        // endregion

        // region patch for disable auto switch mix playlists

        VideoInformationPatch.hook("$PLAYER_CLASS_DESCRIPTOR->disableAutoSwitchMixPlaylists(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JZ)V")

        // endregion

        // region patch for hide channel watermark

        WatermarkFingerprint.resolve(
            context,
            WatermarkParentFingerprint.resultOrThrow().classDef
        )
        WatermarkFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val insertIndex = it.scanResult.patternScanResult!!.endIndex
                val register = getInstruction<TwoRegisterInstruction>(insertIndex).registerA

                addInstructions(
                    insertIndex + 1, """
                        invoke-static {v$register}, $PLAYER_CLASS_DESCRIPTOR->hideChannelWatermark(Z)Z
                        move-result v$register
                        """
                )
            }
        }

        // endregion

        // region patch for hide crowdfunding box

        CrowdfundingBoxFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val insertIndex = it.scanResult.patternScanResult!!.endIndex
                val register = getInstruction<TwoRegisterInstruction>(insertIndex).registerA

                addInstruction(
                    insertIndex,
                    "invoke-static {v$register}, $PLAYER_CLASS_DESCRIPTOR->hideCrowdfundingBox(Landroid/view/View;)V"
                )
            }
        }

        // endregion

        // region patch for hide double-tap overlay filter

        val smaliInstruction = """
            invoke-static {v$REGISTER_TEMPLATE_REPLACEMENT}, $PLAYER_CLASS_DESCRIPTOR->hideDoubleTapOverlayFilter(Landroid/view/View;)V
            """

        arrayOf(
            DarkBackground,
            TapBloomView
        ).forEach { literal ->
            QuickSeekOverlayFingerprint.injectLiteralInstructionViewCall(
                literal,
                smaliInstruction
            )
        }

        // endregion

        // region patch for hide end screen cards

        listOf(
            LayoutCircleFingerprint,
            LayoutIconFingerprint,
            LayoutVideoFingerprint
        ).forEach { fingerprint ->
            fingerprint.resultOrThrow().let {
                it.mutableMethod.apply {
                    val insertIndex = it.scanResult.patternScanResult!!.endIndex
                    val viewRegister = getInstruction<OneRegisterInstruction>(insertIndex).registerA

                    addInstruction(
                        insertIndex + 1,
                        "invoke-static { v$viewRegister }, $PLAYER_CLASS_DESCRIPTOR->hideEndScreenCards(Landroid/view/View;)V"
                    )
                }
            }
        }

        // endregion

        // region patch for hide filmstrip overlay

        FilmStripOverlayParentFingerprint.resultOrThrow().classDef.let { classDef ->
            arrayOf(
                FilmStripOverlayConfigFingerprint,
                FilmStripOverlayInteractionFingerprint,
                FilmStripOverlayPreviewFingerprint
            ).forEach { fingerprint ->
                fingerprint.resolve(context, classDef)
                fingerprint.resultOrThrow().mutableMethod.hook()
            }
        }

        YouTubeControlsOverlayFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val constIndex = indexOfFirstWideLiteralInstructionValueOrThrow(FadeDurationFast)
                val constRegister = getInstruction<OneRegisterInstruction>(constIndex).registerA
                val insertIndex =
                    indexOfFirstInstructionReversedOrThrow(constIndex, Opcode.INVOKE_VIRTUAL) + 1
                val jumpIndex = implementation!!.instructions.let { instruction ->
                    insertIndex + instruction.subList(insertIndex, instruction.size - 1)
                        .indexOfFirst { instructions ->
                            instructions.opcode == Opcode.GOTO || instructions.opcode == Opcode.GOTO_16
                        }
                }

                val replaceInstruction = getInstruction<TwoRegisterInstruction>(insertIndex)
                val replaceReference =
                    getInstruction<ReferenceInstruction>(insertIndex).reference

                addLiteralValues(insertIndex, jumpIndex - 1)

                addInstructionsWithLabels(
                    insertIndex + 1, literalComponent + """
                        const v$constRegister, $FadeDurationFast
                        invoke-static {}, $PLAYER_CLASS_DESCRIPTOR->hideFilmstripOverlay()Z
                        move-result v${replaceInstruction.registerA}
                        if-nez v${replaceInstruction.registerA}, :hidden
                        iget-object v${replaceInstruction.registerA}, v${replaceInstruction.registerB}, $replaceReference
                        """, ExternalLabel("hidden", getInstruction(jumpIndex))
                )
                removeInstruction(insertIndex)
            }
        }

        // endregion

        // region patch for hide info cards

        InfoCardsIncognitoFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val targetIndex = it.scanResult.patternScanResult!!.startIndex
                val targetRegister =
                    getInstruction<TwoRegisterInstruction>(targetIndex).registerA

                addInstructions(
                    targetIndex + 1, """
                        invoke-static {v$targetRegister}, $PLAYER_CLASS_DESCRIPTOR->hideInfoCard(Z)Z
                        move-result v$targetRegister
                        """
                )
            }
        }

        // endregion

        // region patch for hide seek message

        SeekEduContainerFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                addInstructionsWithLabels(
                    0, """
                        invoke-static {}, $PLAYER_CLASS_DESCRIPTOR->hideSeekMessage()Z
                        move-result v0
                        if-eqz v0, :default
                        return-void
                        """, ExternalLabel("default", getInstruction(0))
                )
            }
        }

        YouTubeControlsOverlayFingerprint.resultOrThrow().let { result ->
            result.mutableMethod.apply {
                val insertIndex =
                    indexOfFirstWideLiteralInstructionValueOrThrow(SeekUndoEduOverlayStub)
                val insertRegister = getInstruction<OneRegisterInstruction>(insertIndex).registerA

                val onClickListenerIndex = indexOfFirstInstructionOrThrow(insertIndex) {
                    opcode == Opcode.INVOKE_VIRTUAL &&
                            getReference<MethodReference>()?.name == "setOnClickListener"
                }
                val constComponent = getConstComponent(insertIndex, onClickListenerIndex - 1)

                if (constComponent.isNotEmpty()) {
                    addInstruction(
                        onClickListenerIndex + 2,
                        constComponent
                    )
                }
                addInstructionsWithLabels(
                    insertIndex, """
                        invoke-static {}, $PLAYER_CLASS_DESCRIPTOR->hideSeekUndoMessage()Z
                        move-result v$insertRegister
                        if-nez v$insertRegister, :default
                        """, ExternalLabel("default", getInstruction(onClickListenerIndex + 1))
                )
            }
        }

        // endregion

        // region patch for hide suggested actions

        SuggestedActionsFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val targetIndex = it.scanResult.patternScanResult!!.endIndex
                val targetRegister = getInstruction<OneRegisterInstruction>(targetIndex).registerA

                addInstruction(
                    targetIndex + 1,
                    "invoke-static {v$targetRegister}, $PLAYER_CLASS_DESCRIPTOR->hideSuggestedActions(Landroid/view/View;)V"

                )
            }
        }

        // endregion

        // region patch for skip autoplay countdown

        // This patch works fine when the [SuggestedVideoEndScreenPatch] patch is included.
        TouchAreaOnClickListenerFingerprint.resultOrThrow().let {
            it.mutableClass.methods.find { method ->
                method.parameters == listOf("Landroid/view/View${'$'}OnClickListener;")
            }?.apply {
                val setOnClickListenerIndex = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.INVOKE_VIRTUAL &&
                            getReference<MethodReference>()?.name == "setOnClickListener"
                }
                val setOnClickListenerRegister =
                    getInstruction<FiveRegisterInstruction>(setOnClickListenerIndex).registerC

                addInstruction(
                    setOnClickListenerIndex + 1,
                    "invoke-static {v$setOnClickListenerRegister}, $PLAYER_CLASS_DESCRIPTOR->skipAutoPlayCountdown(Landroid/view/View;)V"
                )
            } ?: throw PatchException("Failed to find setOnClickListener method")
        }

        // endregion

        // region patch for hide video zoom overlay

        VideoZoomSnapIndicatorFingerprint.resultOrThrow().mutableMethod.apply {
            addInstructionsWithLabels(
                0, """
                    invoke-static {}, $PLAYER_CLASS_DESCRIPTOR->hideZoomOverlay()Z
                    move-result v0
                    if-eqz v0, :shown
                    return-void
                    """, ExternalLabel("shown", getInstruction(0))
            )
        }

        // endregion

        InclusiveSpanPatch.addFilter(SANITIZE_VIDEO_SUBTITLE_FILTER_CLASS_DESCRIPTOR)
        LithoFilterPatch.addFilter(PLAYER_COMPONENTS_FILTER_CLASS_DESCRIPTOR)

        /**
         * Add settings
         */
        SettingsPatch.addPreference(
            arrayOf(
                "PREFERENCE_SCREEN: PLAYER",
                "SETTINGS: PLAYER_COMPONENTS"
            )
        )

        SettingsPatch.updatePatchStatus(this)
    }

    private var literalComponent: String = ""

    private fun MutableMethod.addLiteralValues(
        startIndex: Int,
        endIndex: Int
    ) {
        for (index in startIndex..endIndex) {
            val opcode = getInstruction(index).opcode
            if (opcode != Opcode.CONST_16 && opcode != Opcode.CONST_4)
                continue

            val register = getInstruction<OneRegisterInstruction>(index).registerA
            val value = getInstruction<WideLiteralInstruction>(index).wideLiteral.toInt()

            val line = """
                const/16 v$register, $value
                
                """.trimIndent()

            literalComponent += line
        }
    }

    private fun MutableMethod.hook() {
        addInstructionsWithLabels(
            0, """
                invoke-static {}, $PLAYER_CLASS_DESCRIPTOR->hideFilmstripOverlay()Z
                move-result v0
                if-eqz v0, :shown
                const/4 v0, 0x0
                return v0
                """, ExternalLabel("shown", getInstruction(0))
        )
    }

    private fun MutableMethod.getConstComponent(
        startIndex: Int,
        endIndex: Int
    ): String {
        val constRegister =
            getInstruction<FiveRegisterInstruction>(endIndex).registerE

        for (index in endIndex downTo startIndex) {
            val instruction = getInstruction(index)
            if (instruction.opcode != Opcode.CONST_16 && instruction.opcode != Opcode.CONST_4)
                continue

            if ((instruction as OneRegisterInstruction).registerA != constRegister)
                continue

            val constValue = (instruction as WideLiteralInstruction).wideLiteral.toInt()

            return "const/16 v$constRegister, $constValue"
        }
        return ""
    }
}

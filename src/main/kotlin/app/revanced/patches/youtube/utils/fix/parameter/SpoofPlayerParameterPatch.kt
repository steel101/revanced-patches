package app.revanced.patches.youtube.utils.fix.parameter

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.youtube.utils.fix.parameter.fingerprints.ParamsMapPutFingerprint
import app.revanced.patches.youtube.utils.fix.parameter.fingerprints.PlayerResponseModelGeneralStoryboardRendererFingerprint
import app.revanced.patches.youtube.utils.fix.parameter.fingerprints.PlayerResponseModelLiveStreamStoryboardRendererFingerprint
import app.revanced.patches.youtube.utils.fix.parameter.fingerprints.PlayerResponseModelStoryboardRecommendedLevelFingerprint
import app.revanced.patches.youtube.utils.fix.parameter.fingerprints.StatsQueryParameterFingerprint
import app.revanced.patches.youtube.utils.fix.parameter.fingerprints.StoryboardRendererDecoderRecommendedLevelFingerprint
import app.revanced.patches.youtube.utils.fix.parameter.fingerprints.StoryboardRendererDecoderSpecFingerprint
import app.revanced.patches.youtube.utils.fix.parameter.fingerprints.StoryboardRendererSpecFingerprint
import app.revanced.patches.youtube.utils.fix.parameter.fingerprints.StoryboardThumbnailFingerprint
import app.revanced.patches.youtube.utils.fix.parameter.fingerprints.StoryboardThumbnailParentFingerprint
import app.revanced.patches.youtube.utils.integrations.Constants.COMPATIBLE_PACKAGE
import app.revanced.patches.youtube.utils.integrations.Constants.MISC_PATH
import app.revanced.patches.youtube.utils.playerresponse.PlayerResponsePatch
import app.revanced.patches.youtube.utils.playertype.PlayerTypeHookPatch
import app.revanced.patches.youtube.utils.settings.SettingsPatch
import app.revanced.patches.youtube.utils.videoid.general.VideoIdPatch
import app.revanced.util.patch.BaseBytecodePatch
import app.revanced.util.resultOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
object SpoofPlayerParameterPatch : BaseBytecodePatch(
    name = "Spoof player parameters",
    description = "Adds options to spoof player parameters to prevent playback issues.",
    dependencies = setOf(
        PlayerTypeHookPatch::class,
        PlayerResponsePatch::class,
        VideoIdPatch::class,
        SettingsPatch::class
    ),
    compatiblePackages = COMPATIBLE_PACKAGE,
    fingerprints = setOf(
        ParamsMapPutFingerprint,
        PlayerResponseModelGeneralStoryboardRendererFingerprint,
        PlayerResponseModelLiveStreamStoryboardRendererFingerprint,
        PlayerResponseModelStoryboardRecommendedLevelFingerprint,
        StatsQueryParameterFingerprint,
        StoryboardRendererDecoderRecommendedLevelFingerprint,
        StoryboardRendererDecoderSpecFingerprint,
        StoryboardRendererSpecFingerprint,
        StoryboardThumbnailParentFingerprint
    ),
    use = false
) {
    private const val INTEGRATIONS_CLASS_DESCRIPTOR =
        "$MISC_PATH/SpoofPlayerParameterPatch;"

    override fun execute(context: BytecodeContext) {

        // Hook the player parameters.
        PlayerResponsePatch += PlayerResponsePatch.Hook.PlayerParameter(
            "$INTEGRATIONS_CLASS_DESCRIPTOR->spoofParameter(Ljava/lang/String;Ljava/lang/String;Z)Ljava/lang/String;"
        )

        // Force the seekbar time and chapters to always show up.
        // This is used if the storyboard spec fetch fails, for viewing paid videos,
        // or if storyboard spoofing is turned off.
        StoryboardThumbnailParentFingerprint.resultOrThrow().classDef.let { classDef ->
            StoryboardThumbnailFingerprint.also {
                it.resolve(
                    context,
                    classDef
                )
            }.resultOrThrow().let {
                it.mutableMethod.apply {
                    val targetIndex = it.scanResult.patternScanResult!!.endIndex
                    val targetRegister =
                        getInstruction<OneRegisterInstruction>(targetIndex).registerA

                    // Since this is end of the method must replace one line then add the rest.
                    addInstructions(
                        targetIndex + 1,
                        """
                                invoke-static {}, $INTEGRATIONS_CLASS_DESCRIPTOR->getSeekbarThumbnailOverrideValue()Z
                                move-result v$targetRegister
                                return v$targetRegister
                                """
                    )
                    removeInstruction(targetIndex)
                }
            }
        }

        // Hook storyboard renderer url.
        arrayOf(
            PlayerResponseModelGeneralStoryboardRendererFingerprint,
            PlayerResponseModelLiveStreamStoryboardRendererFingerprint
        ).forEach { fingerprint ->
            fingerprint.resultOrThrow().let {
                it.mutableMethod.apply {
                    val getStoryboardIndex = it.scanResult.patternScanResult!!.endIndex
                    val getStoryboardRegister =
                        getInstruction<OneRegisterInstruction>(getStoryboardIndex).registerA

                    addInstructions(
                        getStoryboardIndex,
                        """
                            invoke-static { v$getStoryboardRegister }, $INTEGRATIONS_CLASS_DESCRIPTOR->getStoryboardRendererSpec(Ljava/lang/String;)Ljava/lang/String;
                            move-result-object v$getStoryboardRegister
                            """
                    )
                }
            }
        }

        // Hook recommended seekbar thumbnails quality level.
        StoryboardRendererDecoderRecommendedLevelFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val moveOriginalRecommendedValueIndex = it.scanResult.patternScanResult!!.endIndex
                val originalValueRegister =
                    getInstruction<OneRegisterInstruction>(moveOriginalRecommendedValueIndex).registerA

                addInstructions(
                    moveOriginalRecommendedValueIndex + 1, """
                        invoke-static { v$originalValueRegister }, $INTEGRATIONS_CLASS_DESCRIPTOR->getRecommendedLevel(I)I
                        move-result v$originalValueRegister
                        """
                )
            }
        }

        // Hook the recommended precise seeking thumbnails quality level.
        PlayerResponseModelStoryboardRecommendedLevelFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val moveOriginalRecommendedValueIndex = it.scanResult.patternScanResult!!.endIndex
                val originalValueRegister =
                    getInstruction<OneRegisterInstruction>(moveOriginalRecommendedValueIndex).registerA

                addInstructions(
                    moveOriginalRecommendedValueIndex, """
                        invoke-static { v$originalValueRegister }, $INTEGRATIONS_CLASS_DESCRIPTOR->getRecommendedLevel(I)I
                        move-result v$originalValueRegister
                        """
                )
            }
        }

        StoryboardRendererSpecFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val storyBoardUrlParams = 0

                addInstructionsWithLabels(
                    0, """
                        if-nez p$storyBoardUrlParams, :ignore
                        invoke-static { p$storyBoardUrlParams }, $INTEGRATIONS_CLASS_DESCRIPTOR->getStoryboardRendererSpec(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object p$storyBoardUrlParams
                        """, ExternalLabel("ignore", getInstruction(0))
                )
            }
        }

        // Hook the seekbar thumbnail decoder and use a NULL spec for live streams.
        StoryboardRendererDecoderSpecFingerprint.resultOrThrow().let {
            val storyBoardUrlIndex = it.scanResult.patternScanResult!!.startIndex + 1
            val storyboardUrlRegister =
                it.mutableMethod.getInstruction<OneRegisterInstruction>(storyBoardUrlIndex).registerA

            it.mutableMethod.addInstructions(
                storyBoardUrlIndex + 1, """
                        invoke-static { v$storyboardUrlRegister }, $INTEGRATIONS_CLASS_DESCRIPTOR->getStoryboardDecoderRendererSpec(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v$storyboardUrlRegister
                        """
            )
        }

        // Fix stats not being tracked.
        // Due to signature spoofing "adformat" is present in query parameters made for /stats requests,
        // even though, for regular videos, it should not be.
        // This breaks stats tracking.
        // Replace the ad parameter with the video parameter in the query parameters.
        StatsQueryParameterFingerprint.resultOrThrow().let {
            val putMethod = ParamsMapPutFingerprint.resultOrThrow().method.toString()

            it.mutableMethod.apply {
                val adParamIndex = it.scanResult.stringsScanResult!!.matches.first().index
                val videoParamIndex = adParamIndex + 3

                // Replace the ad parameter with the video parameter.
                replaceInstruction(adParamIndex, getInstruction(videoParamIndex))

                // Call paramsMap.put instead of paramsMap.putIfNotExist
                // because the key is already present in the map.
                val putAdParamIndex = adParamIndex + 1
                val putIfKeyNotExistsInstruction = getInstruction<FiveRegisterInstruction>(putAdParamIndex)
                replaceInstruction(
                    putAdParamIndex,
                    "invoke-virtual { " +
                        "v${putIfKeyNotExistsInstruction.registerC}, " +
                        "v${putIfKeyNotExistsInstruction.registerD}, " +
                        "v${putIfKeyNotExistsInstruction.registerE} }, " +
                        putMethod,
                )
            }
        }

        /**
         * Add settings
         */
        SettingsPatch.addPreference(
            arrayOf(
                "SETTINGS: EXPERIMENTAL_FLAGS",
                "SETTINGS: SPOOF_PLAYER_PARAMETER"
            )
        )

        SettingsPatch.updatePatchStatus("Spoof player parameters")

    }
}

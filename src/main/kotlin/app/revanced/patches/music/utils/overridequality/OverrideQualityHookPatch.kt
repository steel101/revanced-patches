package app.revanced.patches.music.utils.overridequality

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.or
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.revanced.patches.music.utils.integrations.Constants.INTEGRATIONS_PATH
import app.revanced.patches.music.utils.integrations.Constants.VIDEO_PATH
import app.revanced.patches.music.utils.overridequality.fingerprints.VideoQualityListFingerprint
import app.revanced.patches.music.utils.overridequality.fingerprints.VideoQualityPatchFingerprint
import app.revanced.patches.music.utils.overridequality.fingerprints.VideoQualityTextFingerprint
import app.revanced.patches.music.utils.resourceid.SharedResourceIdPatch
import app.revanced.util.resultOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.util.MethodUtil

@Patch(dependencies = [SharedResourceIdPatch::class])
object OverrideQualityHookPatch : BytecodePatch(
    setOf(
        VideoQualityListFingerprint,
        VideoQualityPatchFingerprint,
        VideoQualityTextFingerprint
    )
) {
    private const val INTEGRATIONS_VIDEO_QUALITY_CLASS_DESCRIPTOR =
        "$VIDEO_PATH/VideoQualityPatch;"

    private const val INTEGRATIONS_VIDEO_UTILS_CLASS_DESCRIPTOR =
        "$INTEGRATIONS_PATH/utils/VideoUtils;"

    private lateinit var objectClass: String
    private lateinit var objectMethod: String

    override fun execute(context: BytecodeContext) {

        VideoQualityListFingerprint.resultOrThrow().let {
            val constructorMethod =
                it.mutableClass.methods.first { method -> MethodUtil.isConstructor(method) }
            val overrideMethod =
                it.mutableClass.methods.find { method -> method.parameterTypes.first() == "I" }

            objectClass = it.method.definingClass
            objectMethod = overrideMethod?.name
                ?: throw PatchException("Failed to find hook method")

            constructorMethod.apply {
                addInstruction(
                    2,
                    "sput-object p0, $INTEGRATIONS_VIDEO_QUALITY_CLASS_DESCRIPTOR->qualityClass:$objectClass"
                )
            }

            it.mutableMethod.apply {
                val listIndex = it.scanResult.patternScanResult!!.startIndex
                val listRegister = getInstruction<FiveRegisterInstruction>(listIndex).registerD

                addInstruction(
                    listIndex,
                    "invoke-static {v$listRegister}, $INTEGRATIONS_VIDEO_QUALITY_CLASS_DESCRIPTOR->setVideoQualityList([Ljava/lang/Object;)V"
                )
            }
        }

        VideoQualityPatchFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                it.mutableClass.staticFields.add(
                    ImmutableField(
                        definingClass,
                        "qualityClass",
                        objectClass,
                        AccessFlags.PUBLIC or AccessFlags.STATIC,
                        null,
                        annotations,
                        null
                    ).toMutable()
                )

                addInstructions(
                    0, """
                        sget-object v0, $INTEGRATIONS_VIDEO_QUALITY_CLASS_DESCRIPTOR->qualityClass:$objectClass
                        invoke-virtual {v0, p0}, $objectClass->$objectMethod(I)V
                        """
                )
            }
        }

        VideoQualityTextFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val textIndex = it.scanResult.patternScanResult!!.endIndex
                val textRegister = getInstruction<TwoRegisterInstruction>(textIndex).registerA

                addInstruction(
                    textIndex + 1,
                    "sput-object v$textRegister, $INTEGRATIONS_VIDEO_UTILS_CLASS_DESCRIPTOR->currentQuality:Ljava/lang/String;"
                )
            }
        }
    }
}
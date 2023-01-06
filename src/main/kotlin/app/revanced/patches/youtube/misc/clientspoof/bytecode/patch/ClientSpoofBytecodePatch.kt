package app.revanced.patches.youtube.misc.clientspoof.bytecode.patch

import app.revanced.patcher.annotation.Name
import app.revanced.patcher.annotation.Version
import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.instruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchResult
import app.revanced.patcher.patch.PatchResultSuccess
import app.revanced.patches.youtube.misc.clientspoof.bytecode.fingerprints.UserAgentHeaderBuilderFingerprint
import app.revanced.shared.annotation.YouTubeCompatibility
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction

@Name("client-spoof-bytecode-patch")
@YouTubeCompatibility
@Version("0.0.1")
class ClientSpoofBytecodePatch : BytecodePatch(
    listOf(UserAgentHeaderBuilderFingerprint)
) {
    override fun execute(context: BytecodeContext): PatchResult {
        val result = UserAgentHeaderBuilderFingerprint.result!!
        val method = result.mutableMethod

        val insertIndex = result.scanResult.patternScanResult!!.endIndex
        val packageNameRegister = (method.instruction(insertIndex) as FiveRegisterInstruction).registerD

        val originalPackageName = "com.google.android.youtube"
        method.addInstruction(insertIndex, "const-string v$packageNameRegister, \"$originalPackageName\"")

        return PatchResultSuccess()
    }
}
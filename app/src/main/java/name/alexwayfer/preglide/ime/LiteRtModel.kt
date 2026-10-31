package name.alexwayfer.preglide.ime

import android.content.res.AssetManager
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel

/**
 * A prediction model in the app's assets at [path], run on the CPU by LiteRT. Its buffers serve call after call,
 * so one call runs at a time.
 */
class LiteRtModel(assets: AssetManager, path: String) {
    private val model = CompiledModel.create(assets, path, CompiledModel.Options(Accelerator.CPU))
    private val inputs = model.createInputBuffers()
    private val outputs = model.createOutputBuffers()

    /** How many tokens the model reads: the length of its input, as model-trainer exported it. */
    val context: Int = requireNotNull(model.getInputTensorType(INPUT).layout) { "The model's input has no shape" }
        .dimensions.last()

    /** The model's score of each token of its vocabulary coming after [ids]. */
    @Synchronized
    fun logits(ids: IntArray): FloatArray {
        inputs[0].writeInt(ids)
        model.run(inputs, outputs)
        return outputs[0].readFloat()
    }

    private companion object {
        /** The name litert-torch gives the model's one input. */
        const val INPUT = "args_0"
    }
}

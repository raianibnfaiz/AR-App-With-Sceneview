package io.github.sceneview.sample.armodelviewer.compose

import android.os.Bundle
import com.google.ar.core.Anchor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.filament.Engine
import com.google.ar.core.*
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.firstByTypeOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import io.github.sceneview.sample.SceneViewTheme

private const val kModelFile = "models/damaged_helmet.glb"
private const val kMaxModelInstances = 10

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SceneViewTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val childNodes = rememberNodes()
                    val engine = rememberEngine()
                    val modelLoader = rememberModelLoader(engine)
                    val materialLoader = rememberMaterialLoader(engine)
                    val cameraNode = rememberARCameraNode(engine)
                    val view = rememberView(engine)
                    val collisionSystem = rememberCollisionSystem(view)

                    var planeRenderer by remember { mutableStateOf(true) }
                    var session by remember { mutableStateOf<com.google.ar.core.Session?>(null) }
                    var frame by remember { mutableStateOf<Frame?>(null) }
                    var trackingFailureReason by remember {
                        mutableStateOf<TrackingFailureReason?>(null)
                    }
                    val modelInstances = remember { mutableListOf<ModelInstance>() }

                    ARScene(
                        modifier = Modifier.fillMaxSize(),
                        childNodes = childNodes,
                        engine = engine,
                        view = view,
                        modelLoader = modelLoader,
                        collisionSystem = collisionSystem,
                        sessionConfiguration = { updatedSession, config ->
                            session = updatedSession
                            config.depthMode =
                                when (updatedSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                                    true -> Config.DepthMode.AUTOMATIC
                                    else -> Config.DepthMode.DISABLED
                                }
                            config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                            config.lightEstimationMode =
                                Config.LightEstimationMode.ENVIRONMENTAL_HDR
                        },
                        cameraNode = cameraNode,
                        planeRenderer = planeRenderer,
                        onTrackingFailureChanged = {
                            trackingFailureReason = it
                        },
                        onSessionUpdated = { _, updatedFrame ->
                            frame = updatedFrame
                            // Rest of the onSessionUpdated code...
                        },
                        onGestureListener = rememberOnGestureListener(
                            onSingleTapConfirmed = { motionEvent, node ->
                                if (node == null && session != null) {
                                    val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)
                                    hitResults?.firstByTypeOrNull(
                                        planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)
                                    )?.let { hitResult ->
                                        // Offset positions for the three anchor nodes
                                        val offsets = listOf(
                                            Pose.makeTranslation(0f, 0f, 0f), // Original hit position
                                            Pose.makeTranslation(0.1f, 0f, 0f), // Slightly to the right
                                            Pose.makeTranslation(-0.1f, 0f, 0f) // Slightly to the left
                                        )

                                        offsets.forEach { offset ->
                                            val anchorPose = hitResult.hitPose.compose(offset)
                                            val anchor = session?.createAnchor(anchorPose)

                                            anchor?.let {
                                                childNodes += createAnchorNode(
                                                    engine = engine,
                                                    modelLoader = modelLoader,
                                                    materialLoader = materialLoader,
                                                    modelInstances = modelInstances,
                                                    anchor = it
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    )

                    Text(
                        modifier = Modifier
                            .systemBarsPadding()
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp, start = 32.dp, end = 32.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 28.sp,
                        color = Color.White,
                        text = trackingFailureReason?.let {
                            it.getDescription(LocalContext.current)
                        } ?: if (childNodes.isEmpty()) {
                            stringResource(R.string.point_your_phone_down)
                        } else {
                            stringResource(R.string.tap_anywhere_to_add_model)
                        }
                    )
                }
            }
        }
    }
//create anchore 
    private fun createAnchorNode(
        engine: Engine,
        modelLoader: ModelLoader,
        materialLoader: MaterialLoader,
        modelInstances: MutableList<ModelInstance>,
        anchor: Anchor
    ): AnchorNode {
        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
        val modelNode = ModelNode(
            modelInstance = modelInstances.apply {
                if (isEmpty()) {
                    this += modelLoader.createInstancedModel(kModelFile, kMaxModelInstances)
                }
            }.removeLast(),
            scaleToUnits = 0.5f
        ).apply {
            isEditable = true
        }
        val boundingBoxNode = CubeNode(
            engine,
            size = modelNode.extents,
            center = modelNode.center,
            materialInstance = materialLoader.createColorInstance(Color.White.copy(alpha = 0.5f))
        ).apply {
            isVisible = false
        }
        modelNode.addChildNode(boundingBoxNode)
        anchorNode.addChildNode(modelNode)

        listOf(modelNode, anchorNode).forEach {
            it.onEditingChanged = { editingTransforms ->
                boundingBoxNode.isVisible = editingTransforms.isNotEmpty()
            }
        }
        return anchorNode
    }
}

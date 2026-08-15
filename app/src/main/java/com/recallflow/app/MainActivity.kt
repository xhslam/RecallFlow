package com.recallflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.recallflow.app.ui.RecallFlowApp
import com.recallflow.app.ui.RecallViewModel
import com.recallflow.app.ui.theme.RecallFlowTheme

class MainActivity : ComponentActivity() {
    private val viewModel: RecallViewModel by viewModels {
        RecallViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecallFlowTheme {
                RecallFlowApp(
                    viewModel = viewModel,
                    initialDestination = intent?.getStringExtra(EXTRA_DESTINATION),
                    initialItemId = intent?.getStringExtra(EXTRA_ITEM_ID)
                )
            }
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_ITEM_ID = "item_id"
        const val DESTINATION_TODAY = "today"
        const val DESTINATION_REVIEW = "review"
    }
}

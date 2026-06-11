package tw.kensuke.assetscope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import tw.kensuke.assetscope.data.LocalPortfolioRepository
import tw.kensuke.assetscope.ui.AssetScopeApp
import tw.kensuke.assetscope.ui.theme.AssetScopeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = LocalPortfolioRepository(applicationContext)
        setContent {
            AssetScopeTheme {
                AssetScopeApp(repository = repository)
            }
        }
    }
}


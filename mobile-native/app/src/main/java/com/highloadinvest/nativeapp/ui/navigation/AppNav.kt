package com.highloadinvest.nativeapp.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.highloadinvest.nativeapp.AppContainer
import com.highloadinvest.nativeapp.ui.screens.home.HomeScaffold
import com.highloadinvest.nativeapp.ui.screens.order.LimitOrderScreen
import com.highloadinvest.nativeapp.ui.screens.ticker.TickerDetailScreen
import com.highloadinvest.nativeapp.ui.screens.welcome.LoginScreen
import com.highloadinvest.nativeapp.ui.screens.welcome.RegisterScreen
import com.highloadinvest.nativeapp.ui.screens.welcome.WelcomeScreen
import com.highloadinvest.nativeapp.ui.theme.ThemeMode

private object Routes {
    const val Welcome = "welcome"
    const val Login = "login"
    const val Register = "register"
    const val Home = "home"
    const val Ticker = "ticker/{symbol}"
    const val Order = "order/{symbol}/{side}"
    fun ticker(symbol: String) = "ticker/$symbol"
    fun order(symbol: String, side: String) = "order/$symbol/$side"
}

@Composable
fun AppNav(
    container: AppContainer,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val nav = rememberNavController()
    val session by container.session.sessionFlow.collectAsState(initial = null)
    val initial = if (session != null) Routes.Home else Routes.Welcome

    NavHost(navController = nav, startDestination = initial) {
        composable(Routes.Welcome) {
            WelcomeScreen(
                onLogin = { nav.navigate(Routes.Login) },
                onRegister = { nav.navigate(Routes.Register) }
            )
        }
        composable(Routes.Login) {
            LoginScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onSuccess = {
                    nav.navigate(Routes.Home) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.Register) {
            RegisterScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onSuccess = {
                    nav.navigate(Routes.Home) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.Home) {
            HomeScaffold(
                container = container,
                onTickerClicked = { nav.navigate(Routes.ticker(it)) },
                onLogout = {
                    nav.navigate(Routes.Welcome) { popUpTo(0) { inclusive = true } }
                },
                onThemeChange = onThemeChange,
                currentTheme = themeMode
            )
        }
        composable(
            Routes.Ticker,
            arguments = listOf(navArgument("symbol") { type = NavType.StringType })
        ) { backStackEntry ->
            val symbol = backStackEntry.arguments?.getString("symbol") ?: return@composable
            TickerDetailScreen(
                container = container,
                ticker = symbol,
                onBack = { nav.popBackStack() },
                onTrade = { ticker, side -> nav.navigate(Routes.order(ticker, side)) }
            )
        }
        composable(
            Routes.Order,
            arguments = listOf(
                navArgument("symbol") { type = NavType.StringType },
                navArgument("side") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val symbol = backStackEntry.arguments?.getString("symbol") ?: return@composable
            val side = backStackEntry.arguments?.getString("side") ?: "BUY"
            LimitOrderScreen(
                container = container,
                ticker = symbol,
                side = side,
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() }
            )
        }
    }
}

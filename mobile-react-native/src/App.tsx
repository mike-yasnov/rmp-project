import React from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { NavigationContainer, DefaultTheme, DarkTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ChartPie, LineChart, User as UserIcon } from 'lucide-react-native';
import { ThemeProvider, useTheme } from './ThemeProvider';
import { SessionProvider, useSession } from './session';
import { Welcome } from './screens/Welcome';
import { Login } from './screens/Login';
import { Register } from './screens/Register';
import { Account } from './screens/Account';
import { Market } from './screens/Market';
import { Profile } from './screens/Profile';
import { TickerDetail } from './screens/TickerDetail';
import { LimitOrder } from './screens/LimitOrder';

type RootStack = {
  Welcome: undefined;
  Login: undefined;
  Register: undefined;
  Home: undefined;
  Ticker: { symbol: string };
  Order: { symbol: string; side: 'BUY' | 'SELL' };
};

const Stack = createNativeStackNavigator<RootStack>();
const Tab = createBottomTabNavigator();

function HomeTabs({ navigation }: any) {
  const { palette } = useTheme();
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarStyle: { backgroundColor: palette.surface, borderTopColor: palette.borderSubtle },
        tabBarActiveTintColor: palette.accentBrand,
        tabBarInactiveTintColor: palette.textMuted,
        tabBarIcon: ({ color, size }) => {
          if (route.name === 'Account') return <ChartPie size={size} color={color} />;
          if (route.name === 'Market') return <LineChart size={size} color={color} />;
          return <UserIcon size={size} color={color} />;
        },
      })}
    >
      <Tab.Screen name="Account" options={{ title: 'Счёт' }}>
        {() => (
          <Account
            onTickerPress={(t) => navigation.navigate('Ticker', { symbol: t })}
          />
        )}
      </Tab.Screen>
      <Tab.Screen name="Market" options={{ title: 'Биржа' }}>
        {() => (
          <Market
            onTickerPress={(t) => navigation.navigate('Ticker', { symbol: t })}
          />
        )}
      </Tab.Screen>
      <Tab.Screen name="Profile" options={{ title: 'Профиль' }}>
        {() => <Profile />}
      </Tab.Screen>
    </Tab.Navigator>
  );
}

function RootNav() {
  const { session, loading } = useSession();
  const { palette } = useTheme();
  if (loading) return null;
  const navTheme = palette.isDark
    ? { ...DarkTheme, colors: { ...DarkTheme.colors, background: palette.canvas, card: palette.surface, text: palette.textPrimary, border: palette.borderSubtle, primary: palette.accentBrand } }
    : { ...DefaultTheme, colors: { ...DefaultTheme.colors, background: palette.canvas, card: palette.surface, text: palette.textPrimary, border: palette.borderSubtle, primary: palette.accentBrand } };
  return (
    <NavigationContainer theme={navTheme}>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {!session ? (
          <>
            <Stack.Screen name="Welcome">
              {({ navigation }) => (
                <Welcome
                  onLogin={() => navigation.navigate('Login')}
                  onRegister={() => navigation.navigate('Register')}
                />
              )}
            </Stack.Screen>
            <Stack.Screen name="Login">
              {({ navigation }) => <Login onBack={() => navigation.goBack()} />}
            </Stack.Screen>
            <Stack.Screen name="Register">
              {({ navigation }) => <Register onBack={() => navigation.goBack()} />}
            </Stack.Screen>
          </>
        ) : (
          <>
            <Stack.Screen name="Home" component={HomeTabs} />
            <Stack.Screen name="Ticker">
              {({ navigation, route }) => (
                <TickerDetail
                  ticker={route.params.symbol}
                  onBack={() => navigation.goBack()}
                  onTrade={(t, s) => navigation.navigate('Order', { symbol: t, side: s })}
                />
              )}
            </Stack.Screen>
            <Stack.Screen name="Order">
              {({ navigation, route }) => (
                <LimitOrder
                  ticker={route.params.symbol}
                  side={route.params.side}
                  onBack={() => navigation.goBack()}
                  onDone={() => navigation.goBack()}
                />
              )}
            </Stack.Screen>
          </>
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}

export default function App() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <ThemeProvider>
          <SessionProvider>
            <StatusBar style="auto" />
            <RootNav />
          </SessionProvider>
        </ThemeProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

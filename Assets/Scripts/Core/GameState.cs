namespace NeonShift
{
    public enum GameState
    {
        Loading,
        Menu,       // main menu or level select is showing
        Playing,
        Animating,
        Paused,
        Victory,
        Failed
    }

    public enum AppScreen
    {
        Splash,
        MainMenu,
        WorldSelect,
        LevelSelect,   // world map
        Gameplay,
        Daily,
        Achievements,
        Settings,
        Profile
    }
}

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
        MainMenu,
        LevelSelect,
        Gameplay
    }
}

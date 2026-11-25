using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace NeonShift.EditorTools
{
    /// <summary>
    /// One-time project setup for the prototype:
    ///  - Player Settings: portrait only, product name "NEON SHIFT".
    ///  - Creates Assets/Scenes/Gameplay.unity (empty; GameManager builds everything at runtime)
    ///    and adds it to Build Settings.
    /// Runs automatically the first time the project opens, and is available under the "NEON SHIFT" menu.
    /// </summary>
    [InitializeOnLoad]
    public static class NeonShiftProjectSetup
    {
        const string ScenePath = "Assets/Scenes/Gameplay.unity";
        const string DoneKey = "NeonShift.ProjectSetupDone";

        static NeonShiftProjectSetup()
        {
            EditorApplication.delayCall += AutoSetup;
        }

        static void AutoSetup()
        {
            if (EditorApplication.isPlayingOrWillChangePlaymode) return;
            if (EditorPrefs.GetBool(DoneKey + "." + Application.dataPath, false)) return;
            EditorPrefs.SetBool(DoneKey + "." + Application.dataPath, true);

            ApplyPlayerSettings();
            if (!File.Exists(ScenePath))
            {
                // Only replace the open scene if it is the untouched default one.
                var active = EditorSceneManager.GetActiveScene();
                if (string.IsNullOrEmpty(active.path) && !active.isDirty) CreateGameplayScene();
                else Debug.Log("NEON SHIFT: use menu NEON SHIFT > Open Gameplay Scene to create the scene.");
            }
            AddSceneToBuildSettings();
        }

        [MenuItem("NEON SHIFT/Apply Portrait Player Settings")]
        public static void ApplyPlayerSettings()
        {
            PlayerSettings.productName = "NEON SHIFT";
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.Portrait;
            PlayerSettings.allowedAutorotateToPortrait = true;
            PlayerSettings.allowedAutorotateToPortraitUpsideDown = false;
            PlayerSettings.allowedAutorotateToLandscapeLeft = false;
            PlayerSettings.allowedAutorotateToLandscapeRight = false;
            Debug.Log("NEON SHIFT: Player Settings set to portrait.");
        }

        [MenuItem("NEON SHIFT/Open Gameplay Scene")]
        public static void OpenGameplayScene()
        {
            if (!EditorSceneManager.SaveCurrentModifiedScenesIfUserWantsTo()) return;
            if (!File.Exists(ScenePath)) CreateGameplayScene();
            else EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            AddSceneToBuildSettings();
        }

        static void CreateGameplayScene()
        {
            Directory.CreateDirectory(Path.GetDirectoryName(ScenePath));
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            EditorSceneManager.SaveScene(scene, ScenePath);
            Debug.Log("NEON SHIFT: created " + ScenePath + ". Press Play to start.");
        }

        static void AddSceneToBuildSettings()
        {
            if (!File.Exists(ScenePath)) return;
            var scenes = EditorBuildSettings.scenes.ToList();
            if (scenes.Any(s => s.path == ScenePath)) return;
            scenes.Insert(0, new EditorBuildSettingsScene(ScenePath, true));
            EditorBuildSettings.scenes = scenes.ToArray();
        }
    }
}

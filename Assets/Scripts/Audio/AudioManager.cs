using UnityEngine;

namespace NeonShift
{
    public enum Sfx
    {
        Move,
        Blocked,
        Win,
        Fail,
        Click,
        Undo
    }

    /// <summary>
    /// Sound effects and a soft ambient music loop, all synthesised in code at startup
    /// (no audio files needed). Replace with real clips in Assets/Audio later.
    /// </summary>
    public sealed class AudioManager : MonoBehaviour
    {
        const int Rate = 44100;

        AudioSource sfxSource, musicSource;
        AudioClip[] clips;

        public static AudioManager Create(Transform parent)
        {
            var go = new GameObject("Audio");
            go.transform.SetParent(parent, false);
            var am = go.AddComponent<AudioManager>();
            am.Init();
            return am;
        }

        void Init()
        {
            sfxSource = gameObject.AddComponent<AudioSource>();
            sfxSource.playOnAwake = false;
            musicSource = gameObject.AddComponent<AudioSource>();
            musicSource.playOnAwake = false;
            musicSource.loop = true;
            musicSource.volume = 0.22f;

            clips = new AudioClip[6];
            clips[(int)Sfx.Move] = Tone("move", 0.07f, 660f, 880f, 0.35f, Wave.Sine);
            clips[(int)Sfx.Blocked] = Tone("blocked", 0.12f, 140f, 110f, 0.4f, Wave.Square);
            clips[(int)Sfx.Click] = Tone("click", 0.05f, 1200f, 1000f, 0.25f, Wave.Sine);
            clips[(int)Sfx.Undo] = Tone("undo", 0.08f, 700f, 450f, 0.3f, Wave.Sine);
            clips[(int)Sfx.Win] = Arpeggio("win", new[] { 523.25f, 659.25f, 783.99f, 1046.5f }, 0.09f, 0.35f);
            clips[(int)Sfx.Fail] = Arpeggio("fail", new[] { 392f, 311.13f, 246.94f }, 0.12f, 0.3f);
            musicSource.clip = AmbientLoop();
            ApplySettings();
        }

        public void ApplySettings()
        {
            if (SettingsData.Music) { if (!musicSource.isPlaying) musicSource.Play(); }
            else musicSource.Stop();
        }

        public void Play(Sfx sfx)
        {
            if (!SettingsData.Sound || clips == null) return;
            sfxSource.PlayOneShot(clips[(int)sfx]);
        }

        enum Wave { Sine, Square }

        static AudioClip Tone(string name, float seconds, float f0, float f1, float volume, Wave wave)
        {
            int n = Mathf.CeilToInt(seconds * Rate);
            var data = new float[n];
            double phase = 0;
            for (int i = 0; i < n; i++)
            {
                float t = (float)i / n;
                float f = Mathf.Lerp(f0, f1, t);
                phase += 2.0 * Mathf.PI * f / Rate;
                float s = Mathf.Sin((float)phase);
                if (wave == Wave.Square) s = Mathf.Sign(s) * 0.5f;
                float env = Mathf.Min(1f, i / (Rate * 0.004f)) * Mathf.Pow(1f - t, 2f);
                data[i] = s * env * volume;
            }
            var clip = AudioClip.Create(name, n, 1, Rate, false);
            clip.SetData(data, 0);
            return clip;
        }

        static AudioClip Arpeggio(string name, float[] notes, float noteSeconds, float volume)
        {
            int per = Mathf.CeilToInt(noteSeconds * Rate);
            int tail = Rate / 4;
            int n = per * notes.Length + tail;
            var data = new float[n];
            for (int k = 0; k < notes.Length; k++)
            {
                int start = k * per;
                int len = per + tail;
                for (int i = 0; i < len && start + i < n; i++)
                {
                    float t = (float)i / len;
                    float env = Mathf.Min(1f, i / (Rate * 0.005f)) * Mathf.Exp(-5f * t);
                    float s = Mathf.Sin(2f * Mathf.PI * notes[k] * i / Rate) + 0.3f * Mathf.Sin(4f * Mathf.PI * notes[k] * i / Rate);
                    data[start + i] += s * env * volume * 0.6f;
                }
            }
            var clip = AudioClip.Create(name, n, 1, Rate, false);
            clip.SetData(data, 0);
            return clip;
        }

        /// <summary>16-second loop of slow, soft chords (A minor - F - C - G).</summary>
        static AudioClip AmbientLoop()
        {
            const float chordSeconds = 4f;
            float[][] chords =
            {
                new[] { 220f, 261.63f, 329.63f },
                new[] { 174.61f, 220f, 261.63f },
                new[] { 130.81f, 196f, 261.63f },
                new[] { 196f, 246.94f, 293.66f }
            };
            int per = (int)(chordSeconds * Rate);
            int n = per * chords.Length;
            var data = new float[n];
            for (int c = 0; c < chords.Length; c++)
            {
                for (int i = 0; i < per; i++)
                {
                    float t = (float)i / per;
                    float env = Mathf.Sin(t * Mathf.PI); // fade in and out per chord
                    float s = 0f;
                    foreach (float f in chords[c])
                    {
                        float time = (float)(c * per + i) / Rate;
                        s += Mathf.Sin(2f * Mathf.PI * f * time) + 0.15f * Mathf.Sin(2f * Mathf.PI * f * 2.003f * time);
                    }
                    data[c * per + i] = s / chords[c].Length * env * 0.35f;
                }
            }
            var clip = AudioClip.Create("ambient", n, 1, Rate, false);
            clip.SetData(data, 0);
            return clip;
        }
    }
}

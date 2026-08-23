[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\client\res\customclient\music')
)

$ErrorActionPreference = 'Stop'
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($resolvedOutput) | Out-Null

Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Text;

public static class MoonFlowerMusicGenerator
{
    private const int SampleRate = 44100;

    public static void Create(string path, int seconds, int variant)
    {
        int frames = SampleRate * seconds;
        byte[] data = new byte[frames * 4];
        uint noiseState = (uint)(0x4d4f4f4e + variant * 7919);
        double filteredNoise = 0.0;
        double[] roots = variant == 0
            ? new double[] { 110.00, 130.81, 98.00, 146.83 }
            : new double[] { 130.81, 146.83, 110.00, 164.81 };
        double[] bells = variant == 0
            ? new double[] { 523.25, 659.25, 783.99, 587.33, 659.25, 880.00, 783.99, 659.25 }
            : new double[] { 659.25, 783.99, 987.77, 880.00, 783.99, 659.25, 587.33, 659.25 };

        for(int i = 0; i < frames; i++) {
            double time = i / (double)SampleRate;
            double edge = Math.Min(1.0, Math.Min(time / 2.0, (seconds - time) / 2.0));
            edge = edge * edge * (3.0 - 2.0 * edge);

            double sectionLength = seconds / 4.0;
            int section = Math.Min(3, (int)(time / sectionLength));
            double root = roots[section];
            double local = (time - section * sectionLength) / sectionLength;
            double cross = Math.Min(1.0, Math.Min(local * 5.0, (1.0 - local) * 5.0));
            cross = Math.Max(0.20, cross);

            double pad = Math.Sin(2.0 * Math.PI * root * time) * 0.22;
            pad += Math.Sin(2.0 * Math.PI * root * 1.5 * time + 0.7) * 0.16;
            pad += Math.Sin(2.0 * Math.PI * root * 2.0 * time + 1.1) * 0.09;
            pad += Math.Sin(2.0 * Math.PI * root * 0.5 * time + 0.3) * 0.13;
            pad *= 0.22 * cross;

            double bellSpacing = seconds / (double)bells.Length;
            int bellIndex = Math.Min(bells.Length - 1, (int)(time / bellSpacing));
            double bellAge = time - bellIndex * bellSpacing;
            double bellEnv = bellAge < 2.8 ? Math.Exp(-bellAge * 1.65) : 0.0;
            double bellFreq = bells[bellIndex];
            double bell = Math.Sin(2.0 * Math.PI * bellFreq * time) * 0.16;
            bell += Math.Sin(2.0 * Math.PI * bellFreq * 2.01 * time) * 0.055;
            bell += Math.Sin(2.0 * Math.PI * bellFreq * 3.98 * time) * 0.025;
            bell *= bellEnv;

            noiseState = 1664525u * noiseState + 1013904223u;
            double rawNoise = ((noiseState >> 8) / 16777215.0) * 2.0 - 1.0;
            filteredNoise += (rawNoise - filteredNoise) * 0.004;
            double air = filteredNoise * 0.025;

            double baseSample = (pad + bell + air) * edge;
            double sway = Math.Sin(2.0 * Math.PI * 0.035 * time);
            double left = baseSample * (0.94 + sway * 0.06);
            double right = (pad + bell * 0.88 + air) * edge * (0.94 - sway * 0.06);
            short leftPcm = ToPcm(left);
            short rightPcm = ToPcm(right);
            int offset = i * 4;
            data[offset] = (byte)(leftPcm & 0xff);
            data[offset + 1] = (byte)((leftPcm >> 8) & 0xff);
            data[offset + 2] = (byte)(rightPcm & 0xff);
            data[offset + 3] = (byte)((rightPcm >> 8) & 0xff);
        }

        using(var stream = File.Create(path))
        using(var writer = new BinaryWriter(stream)) {
            writer.Write(Encoding.ASCII.GetBytes("RIFF"));
            writer.Write(36 + data.Length);
            writer.Write(Encoding.ASCII.GetBytes("WAVE"));
            writer.Write(Encoding.ASCII.GetBytes("fmt "));
            writer.Write(16);
            writer.Write((short)1);
            writer.Write((short)2);
            writer.Write(SampleRate);
            writer.Write(SampleRate * 4);
            writer.Write((short)4);
            writer.Write((short)16);
            writer.Write(Encoding.ASCII.GetBytes("data"));
            writer.Write(data.Length);
            writer.Write(data);
        }
    }

    private static short ToPcm(double sample)
    {
        sample = Math.Max(-0.96, Math.Min(0.96, sample));
        return (short)Math.Round(sample * short.MaxValue);
    }
}
'@

$loginPath = Join-Path $resolvedOutput 'moonflower-login.wav'
$characterPath = Join-Path $resolvedOutput 'moonflower-homecoming.wav'
[MoonFlowerMusicGenerator]::Create($loginPath, 32, 0)
[MoonFlowerMusicGenerator]::Create($characterPath, 28, 1)

Get-Item -LiteralPath $loginPath, $characterPath |
    Select-Object FullName, Length, LastWriteTime

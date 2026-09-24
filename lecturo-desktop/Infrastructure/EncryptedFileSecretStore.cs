using System.Security.Cryptography;
using System.Text.Json;
using Lecturo.Application.Settings;

namespace Lecturo.Infrastructure;

/// <summary>
/// Fallback secret store when no OS keychain is available: an AES-256-GCM
/// encrypted JSON file. The key file only has filesystem permissions (0600)
/// protecting it, so this is weaker than the keychain — <see cref="Warning"/>
/// says so and the settings screen surfaces it.
/// </summary>
public sealed class EncryptedFileSecretStore : ISecretStore
{
    private readonly string _secretsPath;
    private readonly string _keyPath;

    public string? Warning =>
        "OS keychain unavailable — secrets are stored in an encrypted file protected only by filesystem permissions.";

    public EncryptedFileSecretStore(string secretsPath, string keyPath)
    {
        _secretsPath = secretsPath;
        _keyPath = keyPath;
        EnsureKey();
    }

    public string? Get(string key) => LoadAll().TryGetValue(key, out var value) ? value : null;

    public void Set(string key, string value)
    {
        var all = LoadAll();
        all[key] = value;
        SaveAll(all);
    }

    public void Delete(string key)
    {
        var all = LoadAll();
        if (all.Remove(key))
            SaveAll(all);
    }

    private void EnsureKey()
    {
        if (File.Exists(_keyPath))
            return;
        var key = RandomNumberGenerator.GetBytes(32);
        File.WriteAllBytes(_keyPath, key);
        RestrictToUser(_keyPath);
    }

    /// <summary>
    /// Owner-only permissions on Unix; on Windows the file already lives under the
    /// user's profile, and Unix modes do not exist.
    /// </summary>
    private static void RestrictToUser(string path)
    {
        if (!OperatingSystem.IsWindows())
            File.SetUnixFileMode(path, UnixFileMode.UserRead | UnixFileMode.UserWrite);
    }

    private byte[] ReadKey() => File.ReadAllBytes(_keyPath);

    private Dictionary<string, string> LoadAll()
    {
        if (!File.Exists(_secretsPath))
            return new Dictionary<string, string>();
        var blob = File.ReadAllBytes(_secretsPath);
        var nonce = blob.AsSpan(0, 12);
        var tag = blob.AsSpan(12, 16);
        var cipher = blob.AsSpan(28);
        var plain = new byte[cipher.Length];
        using var aes = new AesGcm(ReadKey(), 16);
        aes.Decrypt(nonce, cipher, tag, plain);
        return JsonSerializer.Deserialize<Dictionary<string, string>>(plain) ?? new Dictionary<string, string>();
    }

    private void SaveAll(Dictionary<string, string> all)
    {
        var plain = JsonSerializer.SerializeToUtf8Bytes(all);
        var nonce = RandomNumberGenerator.GetBytes(12);
        var cipher = new byte[plain.Length];
        var tag = new byte[16];
        using var aes = new AesGcm(ReadKey(), 16);
        aes.Encrypt(nonce, plain, cipher, tag);
        using var stream = File.Create(_secretsPath);
        stream.Write(nonce);
        stream.Write(tag);
        stream.Write(cipher);
        RestrictToUser(_secretsPath);
    }
}

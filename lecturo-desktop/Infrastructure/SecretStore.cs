using Lecturo.Application.Settings;

namespace Lecturo.Infrastructure;

public static class SecretStore
{
    /// <summary>
    /// The best store the platform has: the libsecret keyring on Linux, otherwise
    /// the AES-encrypted file (the keychain calls are Linux-only P/Invoke).
    /// </summary>
    public static ISecretStore Create()
    {
        if (OperatingSystem.IsLinux())
        {
            try
            {
                var store = new LibSecretStore();
                store.Probe();
                return store;
            }
            catch
            {
                // Fall through to the encrypted file.
            }
        }

        return new EncryptedFileSecretStore(
            Path.Combine(AppPaths.ConfigDir, "secrets.enc"),
            Path.Combine(AppPaths.ConfigDir, ".secret-key"));
    }
}

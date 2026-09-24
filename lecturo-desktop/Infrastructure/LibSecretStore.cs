using System.Runtime.InteropServices;
using System.Text;
using Lecturo.Application.Settings;

namespace Lecturo.Infrastructure;

/// <summary>
/// Secret store backed by the GNOME/libsecret keyring (org.freedesktop.Secret
/// simple API) via P/Invoke. libsecret is present as a WebKitGTK dependency.
/// </summary>
public sealed class LibSecretStore : ISecretStore
{
    private const string Lib = "libsecret-1.so.0";

    private const int SecretSchemaNone = 0;
    private const int SecretSchemaAttributeString = 0;

    private readonly IntPtr _schema;

    public string? Warning => null;

    public LibSecretStore()
    {
        _schema = secret_schema_new("org.lecturo.Secrets", SecretSchemaNone,
            "key", SecretSchemaAttributeString, IntPtr.Zero);
        if (_schema == IntPtr.Zero)
            throw new InvalidOperationException("Failed to create libsecret schema.");
    }

    /// <summary>Throws if the keyring is unreachable (no D-Bus session, headless, etc.).</summary>
    public void Probe()
    {
        const string probeKey = "__probe__";
        Set(probeKey, "1");
        var value = Get(probeKey);
        Delete(probeKey);
        if (value != "1")
            throw new InvalidOperationException("libsecret probe failed.");
    }

    public string? Get(string key)
    {
        var ptr = secret_password_lookup_sync(_schema, IntPtr.Zero, out var error,
            "key", key, IntPtr.Zero);
        ThrowOnError(error);
        if (ptr == IntPtr.Zero)
            return null;
        try
        {
            return PtrToStringUTF8(ptr);
        }
        finally
        {
            secret_password_free(ptr);
        }
    }

    public void Set(string key, string value)
    {
        var ok = secret_password_store_sync(_schema, IntPtr.Zero, $"lecturo: {key}", value,
            IntPtr.Zero, out var error, "key", key, IntPtr.Zero);
        ThrowOnError(error);
        if (!ok)
            throw new InvalidOperationException($"libsecret failed to store '{key}'.");
    }

    public void Delete(string key)
    {
        var ok = secret_password_clear_sync(_schema, IntPtr.Zero, out var error,
            "key", key, IntPtr.Zero);
        ThrowOnError(error);
        _ = ok; // clearing a non-existent entry is not an error
    }

    private static void ThrowOnError(IntPtr error)
    {
        if (error != IntPtr.Zero)
            throw new InvalidOperationException("libsecret operation failed (is a D-Bus session/keyring available?).");
    }

    private static string PtrToStringUTF8(IntPtr ptr)
    {
        var len = 0;
        while (Marshal.ReadByte(ptr, len) != 0) len++;
        var bytes = new byte[len];
        Marshal.Copy(ptr, bytes, 0, len);
        return Encoding.UTF8.GetString(bytes);
    }

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr secret_schema_new(
        [MarshalAs(UnmanagedType.LPUTF8Str)] string name, int flags,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string attrName, int attrType,
        IntPtr terminator);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    private static extern bool secret_password_store_sync(
        IntPtr schema, IntPtr collection,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string label,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string password,
        IntPtr cancellable, out IntPtr error,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string attrName,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string attrValue,
        IntPtr terminator);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr secret_password_lookup_sync(
        IntPtr schema, IntPtr cancellable, out IntPtr error,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string attrName,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string attrValue,
        IntPtr terminator);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    private static extern bool secret_password_clear_sync(
        IntPtr schema, IntPtr cancellable, out IntPtr error,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string attrName,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string attrValue,
        IntPtr terminator);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    private static extern void secret_password_free(IntPtr password);
}

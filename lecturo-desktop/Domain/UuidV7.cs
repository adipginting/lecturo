using System.Security.Cryptography;

namespace Lecturo.Domain;

/// <summary>
/// Generates UUIDv7 identifiers (RFC 9562): a 48-bit big-endian Unix-millisecond
/// timestamp followed by randomness, rendered as a lowercase hyphenated string.
/// The timestamp leads, so the ids sort lexicographically by creation time — the
/// ordering the Android and Web clients rely on.
/// </summary>
public static class UuidV7
{
    /// <summary>A fresh, time-ordered identifier.</summary>
    public static string New()
    {
        Span<byte> bytes = stackalloc byte[16];

        var millis = (ulong)DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        bytes[0] = (byte)(millis >> 40);
        bytes[1] = (byte)(millis >> 32);
        bytes[2] = (byte)(millis >> 24);
        bytes[3] = (byte)(millis >> 16);
        bytes[4] = (byte)(millis >> 8);
        bytes[5] = (byte)millis;

        // The remaining ten bytes are random; the version nibble and variant bits
        // are then overwritten as RFC 9562 requires.
        RandomNumberGenerator.Fill(bytes[6..]);
        bytes[6] = (byte)((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte)((bytes[8] & 0x3F) | 0x80);

        return Format(bytes);
    }

    private static string Format(ReadOnlySpan<byte> bytes)
    {
        const string hex = "0123456789abcdef";
        Span<char> chars = stackalloc char[36];
        var at = 0;
        for (var i = 0; i < 16; i++)
        {
            if (i == 4 || i == 6 || i == 8 || i == 10)
                chars[at++] = '-';
            chars[at++] = hex[bytes[i] >> 4];
            chars[at++] = hex[bytes[i] & 0x0F];
        }
        return new string(chars);
    }
}

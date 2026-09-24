namespace Lecturo.Domain;

/// <summary>Which kind of document a file is — the two kinds the library accepts.</summary>
public enum DocumentFormat
{
    Pdf,
    Epub,
}

public static class DocumentFormatExtensions
{
    /// <summary>
    /// The value stored in the database. Stable: existing rows and migration 1
    /// depend on these strings, so they are not a display concern.
    /// </summary>
    public static string ToStoredValue(this DocumentFormat format) => format switch
    {
        DocumentFormat.Pdf => "pdf",
        DocumentFormat.Epub => "epub",
        _ => throw new ArgumentOutOfRangeException(nameof(format), format, "Unknown document format."),
    };

    /// <summary>The value shown to the reader: "PDF", "EPUB".</summary>
    public static string ToLabel(this DocumentFormat format) => format.ToStoredValue().ToUpperInvariant();

    /// <summary>Reads a stored value, or null when the row carries something unrecognised.</summary>
    public static DocumentFormat? FromStoredValue(string? value) => value?.ToLowerInvariant() switch
    {
        "pdf" => DocumentFormat.Pdf,
        "epub" => DocumentFormat.Epub,
        _ => null,
    };

    /// <summary>Reads a stored value that must be one of the known formats.</summary>
    public static DocumentFormat ParseStoredValue(string value) =>
        FromStoredValue(value)
        ?? throw new FormatException($"Unknown document format: \u201c{value}\u201d.");
}

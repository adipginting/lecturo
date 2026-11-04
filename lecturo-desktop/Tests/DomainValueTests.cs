using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// The values stored in the database, and what a row written by an older version
/// of the app still reads as.
/// </summary>
public class DomainValueTests
{
    [Theory]
    [InlineData("pdf", DocumentFormat.Pdf)]
    [InlineData("PDF", DocumentFormat.Pdf)]
    [InlineData("epub", DocumentFormat.Epub)]
    public void A_stored_format_reads_back(string stored, DocumentFormat expected)
    {
        Assert.Equal(expected, DocumentFormatExtensions.FromStoredValue(stored));
    }

    [Fact]
    public void An_unknown_format_is_not_a_new_format()
    {
        Assert.Null(DocumentFormatExtensions.FromStoredValue("djvu"));
        Assert.Throws<FormatException>(() => DocumentFormatExtensions.ParseStoredValue("djvu"));
    }

    [Fact]
    public void A_format_round_trips_through_storage()
    {
        foreach (var format in Enum.GetValues<DocumentFormat>())
            Assert.Equal(format, DocumentFormatExtensions.FromStoredValue(format.ToStoredValue()));
    }

    [Fact]
    public void A_failure_written_before_the_current_spelling_still_reads_as_failed()
    {
        Assert.Equal(RunStatus.Failed, RunStatusExtensions.FromStoredValue("error"));
        Assert.Equal(RunStatus.Failed, RunStatusExtensions.FromStoredValue("failed"));
        Assert.Equal(RunStatus.Done, RunStatusExtensions.FromStoredValue("done"));
        Assert.Null(RunStatusExtensions.FromStoredValue("melted"));
    }

    [Fact]
    public void A_summary_is_sent_to_a_provider_as_an_assistant_turn()
    {
        Assert.Equal("assistant", MessageRole.Summary.ToProviderRole());
        Assert.Equal("user", MessageRole.User.ToProviderRole());
        Assert.Equal("summary", MessageRole.Summary.ToStoredValue());
    }

    [Fact]
    public void An_excerpt_is_text_or_a_picture_and_says_which()
    {
        Assert.False(Excerpt.FromText("words").IsEmpty);
        Assert.True(Excerpt.FromImage("/tmp/crop.png").HasImage);
        Assert.False(Excerpt.FromImage("/tmp/crop.png").IsEmpty);
        Assert.True(new Excerpt().IsEmpty);
        Assert.Equal("", new Excerpt().TextOrEmpty);
    }

    [Fact]
    public void A_remote_item_is_identified_by_source_and_remote_id()
    {
        Assert.Equal("calibre\u001f42", Provenance.Key("calibre", "42"));
        Assert.NotEqual(Provenance.Key("calibre", "42"), Provenance.Key("zotero", "42"));
    }

    [Fact]
    public void A_uuid_v7_is_lowercase_hyphenated_and_ordered_by_time()
    {
        var first = UuidV7.New();

        Assert.Equal(36, first.Length);
        Assert.Equal('7', first[14]);
        Assert.True("89ab".Contains(first[19]), "The variant bits are not RFC 9562.");
        Assert.True(first.All(c => char.IsAsciiHexDigit(c) || c == '-'), "Not a lowercase hyphenated hex id.");

        // The 48-bit big-endian timestamp leads, so a later id sorts after the
        // first as soon as the millisecond advances.
        var later = first;
        while (later[..13] == first[..13])
            later = UuidV7.New();
        Assert.True(string.CompareOrdinal(first, later) < 0);
    }
}

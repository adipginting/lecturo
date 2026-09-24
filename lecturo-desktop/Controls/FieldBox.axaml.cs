using Avalonia;
using Avalonia.Controls;
using Avalonia.Markup.Xaml;

namespace Lecturo.Controls;

/// <summary>An outlined Material 3 text field: a small label above a borderless input.</summary>
public partial class FieldBox : UserControl
{
    public static readonly StyledProperty<string> LabelProperty =
        AvaloniaProperty.Register<FieldBox, string>(nameof(Label), string.Empty);

    public static readonly StyledProperty<string> TextProperty =
        AvaloniaProperty.Register<FieldBox, string>(
            nameof(Text),
            string.Empty,
            defaultBindingMode: Avalonia.Data.BindingMode.TwoWay);

    public static readonly StyledProperty<bool> IsPasswordProperty =
        AvaloniaProperty.Register<FieldBox, bool>(nameof(IsPassword));

    public string Label
    {
        get => GetValue(LabelProperty);
        set => SetValue(LabelProperty, value);
    }

    public string Text
    {
        get => GetValue(TextProperty);
        set => SetValue(TextProperty, value);
    }

    public bool IsPassword
    {
        get => GetValue(IsPasswordProperty);
        set => SetValue(IsPasswordProperty, value);
    }

    private TextBox? _input;

    public FieldBox()
    {
        AvaloniaXamlLoader.Load(this);
        _input = this.FindControl<TextBox>("Input");
        ApplyPassword();
    }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == IsPasswordProperty)
            ApplyPassword();
    }

    private void ApplyPassword()
    {
        if (_input is not null)
            _input.PasswordChar = IsPassword ? '\u2022' : '\0';
    }
}

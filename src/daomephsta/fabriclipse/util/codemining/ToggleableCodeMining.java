package daomephsta.fabriclipse.util.codemining;

import java.util.function.Consumer;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.codemining.AbstractCodeMining;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.jface.text.source.inlined.Positions;
import org.eclipse.swt.events.MouseEvent;

// Minings are toggled instead of the provider returning an empty list. The latter is ideal,
// but it's unclear how to get Eclipse to recompute and draw minings when the toggle changes.
public class ToggleableCodeMining extends AbstractCodeMining
{
    private final int line, column;
    private final String prefQualifier;
    private final IPreferenceChangeListener prefChangeListener;
    private boolean active;

    private ToggleableCodeMining(Position position, ICodeMiningProvider provider,
        Consumer<MouseEvent> action, int line, int column, String fullPrefKey)
    {
        super(position, provider, action);
        this.line = line;
        this.column = column;
        int prefSeparator = fullPrefKey.lastIndexOf('.');
        this.prefQualifier = fullPrefKey.substring(0, prefSeparator);
        String prefKey = fullPrefKey.substring(prefSeparator + 1);
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(prefQualifier);
        this.active = prefs.getBoolean(prefKey, false);
        this.prefChangeListener = e ->
        {
            if (e.getKey().equals(prefKey))
                this.active = Boolean.valueOf((String) e.getNewValue());
        };
        prefs.addPreferenceChangeListener(prefChangeListener);
    }

    public static ToggleableCodeMining inline(Position position, IDocument document,
        ICodeMiningProvider provider, Consumer<MouseEvent> clickAction, String prefKey)
        throws BadLocationException
    {
        int line = document.getLineOfOffset(position.getOffset());
        int column = position.getOffset() - document.getLineOffset(line);
        return new ToggleableCodeMining(position, provider, clickAction,
            line, column, prefKey);
    }

    public static ToggleableCodeMining header(int beforeLineNumber, IDocument document,
        ICodeMiningProvider provider, Consumer<MouseEvent> clickAction, String prefKey)
        throws BadLocationException
    {
        Position position = Positions.of(beforeLineNumber, document, true);
        return new ToggleableCodeMining(position, provider, clickAction,
            beforeLineNumber - 1, 0, prefKey);
    }

    public boolean isActive()
    {
        return active;
    }

    @Override
    public String getLabel()
    {
        return isActive() ? super.getLabel() : null;
    }

    @Override
    public Consumer<MouseEvent> getAction()
    {
        return isActive() ? super.getAction() : null;
    }

    @Override
    public void dispose()
    {
        InstanceScope.INSTANCE.getNode(prefQualifier).removePreferenceChangeListener(prefChangeListener);
        super.dispose();
    }

    @Override
    public String toString()
    {
        if (column == 0)
            return String.format("L%d: %s", line, super.getLabel());
        else
            return String.format("L%d @ %d: %s", line, column, super.getLabel());
    }
}

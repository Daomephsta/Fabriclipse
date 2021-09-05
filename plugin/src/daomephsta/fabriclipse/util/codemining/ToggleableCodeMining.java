package daomephsta.fabriclipse.util.codemining;

import java.util.function.Consumer;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.codemining.AbstractCodeMining;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.jface.text.codemining.LineContentCodeMining;
import org.eclipse.jface.text.codemining.LineHeaderCodeMining;
import org.eclipse.swt.events.MouseEvent;

// Minings are toggled instead of the provider returning an empty list. The latter is ideal,
// but it's unclear how to get Eclipse to recompute and draw minings when the toggle changes.
public abstract class ToggleableCodeMining
{
    private static class Toggle implements IPreferenceChangeListener
    {
        private final String prefQualifier, prefKey;
        private boolean active;

        Toggle(String fullPrefKey)
        {
            int prefSeparator = fullPrefKey.lastIndexOf('.');
            this.prefQualifier = fullPrefKey.substring(0, prefSeparator);
            this.prefKey = fullPrefKey.substring(prefSeparator + 1);
            IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(prefQualifier);
            this.active = prefs.getBoolean(prefKey, false);
            prefs.addPreferenceChangeListener(this);
        }

        @Override
        public void preferenceChange(PreferenceChangeEvent e)
        {
            if (e.getKey().equals(prefKey))
                this.active = Boolean.valueOf((String) e.getNewValue());
        }

        public void dispose()
        {
            InstanceScope.INSTANCE.getNode(prefQualifier).removePreferenceChangeListener(this);
        }
    }

    public static AbstractCodeMining inline(Position position, IDocument document,
        ICodeMiningProvider provider, Consumer<MouseEvent> clickAction, String prefKey)
        throws BadLocationException
    {
        int line = document.getLineOfOffset(position.getOffset());
        int column = position.getOffset() - document.getLineOffset(line);
        return new Inline(position, provider, clickAction,
            line, column, new Toggle(prefKey));
    }

    public static AbstractCodeMining header(int line, IDocument document,
        ICodeMiningProvider provider, Consumer<MouseEvent> clickAction, String prefKey)
        throws BadLocationException
    {
        return new Header(line, document, provider, clickAction, new Toggle(prefKey));
    }

    private static class Inline extends LineContentCodeMining
    {
        private final int line, column;
        private final Toggle toggle;

        private Inline(Position position, ICodeMiningProvider provider,
            Consumer<MouseEvent> clickAction, int line, int column, Toggle toggle)
        {
            super(position, provider, clickAction);
            this.line = line;
            this.column = column;
            this.toggle = toggle;
        }

        @Override
        public String getLabel()
        {
            return toggle.active ? super.getLabel() : null;
        }

        @Override
        public Consumer<MouseEvent> getAction()
        {
            return toggle.active ? super.getAction() : null;
        }

        @Override
        public void dispose()
        {
            super.dispose();
            toggle.dispose();
        }

        @Override
        public String toString()
        {
            return String.format("L%d @ %d: %s", line, column, super.getLabel());
        }
    }

    // Must extend LineHeaderCodeMining, as it is special cased
    private static class Header extends LineHeaderCodeMining
    {
        private final int line;
        private final Toggle toggle;

        private Header(int line, IDocument document, ICodeMiningProvider provider,
            Consumer<MouseEvent> action, Toggle toggle) throws BadLocationException
        {
            super(line, document, provider, action);
            this.line = line;
            this.toggle = toggle;
        }

        @Override
        public String getLabel()
        {
            return toggle.active ? super.getLabel() : null;
        }

        @Override
        public Consumer<MouseEvent> getAction()
        {
            return toggle.active ? super.getAction() : null;
        }

        @Override
        public void dispose()
        {
            super.dispose();
            toggle.dispose();
        }

        @Override
        public String toString()
        {
            return String.format("L%d: %s", line, super.getLabel());
        }
    }
}

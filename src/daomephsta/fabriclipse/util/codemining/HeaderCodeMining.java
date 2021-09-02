package daomephsta.fabriclipse.util.codemining;

import java.util.function.Consumer;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.jface.text.codemining.LineHeaderCodeMining;
import org.eclipse.swt.events.MouseEvent;

public class HeaderCodeMining extends LineHeaderCodeMining
{
    private final int line;

    public HeaderCodeMining(int beforeLineNumber, IDocument document,
        ICodeMiningProvider provider, Consumer<MouseEvent> clickAction)
        throws BadLocationException
    {
        super(beforeLineNumber, document, provider, clickAction);
        this.line = beforeLineNumber - 1;
    }

    @Override
    public String toString()
    {
        return String.format("L%d: %s", line, getLabel());
    }
}
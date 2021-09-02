package daomephsta.fabriclipse.util.codemining;

import java.util.function.Consumer;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.jface.text.codemining.LineContentCodeMining;
import org.eclipse.swt.events.MouseEvent;

public class InlineCodeMining extends LineContentCodeMining
{
    private final int line, column;

    public InlineCodeMining(Position position, IDocument document,
        ICodeMiningProvider provider, Consumer<MouseEvent> clickAction)
        throws BadLocationException
    {
        super(position, provider, clickAction);
        this.line = document.getLineOfOffset(position.getOffset());
        this.column = position.getOffset() - document.getLineOffset(line);
    }

    @Override
    public String toString()
    {
        return String.format("L%d @ %d: %s", line, column, getLabel());
    }
}
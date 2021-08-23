package daomephsta.fabriclipse.util;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.ui.IWorkbenchPart;

public class WorkbenchPartIdPropertyTester extends PropertyTester
{
    public WorkbenchPartIdPropertyTester() {}

    @Override
    public boolean test(Object receiver, String property, Object[] args, Object expectedValue)
    {
        return ((IWorkbenchPart) receiver).getSite().getId().equals(expectedValue);
    }
}

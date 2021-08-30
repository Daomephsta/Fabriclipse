package daomephsta.fabriclipse;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import daomephsta.fabriclipse.metadata.ModMetadataStore;
import daomephsta.fabriclipse.mixin.MixinStore;

public class Fabriclipse extends AbstractUIPlugin
{
    @Override
    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        ResourcesPlugin.getWorkspace().addResourceChangeListener(MixinStore.INSTANCE);
        ResourcesPlugin.getWorkspace().addResourceChangeListener(ModMetadataStore.INSTANCE);
    }
}

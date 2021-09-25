package daomephsta.fabriclipse;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import daomephsta.fabriclipse.metadata.ProjectEnvironmentManager;
import daomephsta.fabriclipse.mixin.MixinStore;

public class Fabriclipse extends AbstractUIPlugin
{
    public static final ILog LOGGER = Platform.getLog(Fabriclipse.class);

    @Override
    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        ResourcesPlugin.getWorkspace().addResourceChangeListener(
            MixinStore.INSTANCE, IResourceChangeEvent.POST_CHANGE);
        ResourcesPlugin.getWorkspace().addResourceChangeListener(
            ProjectEnvironmentManager.INSTANCE, IResourceChangeEvent.POST_CHANGE);
    }
}

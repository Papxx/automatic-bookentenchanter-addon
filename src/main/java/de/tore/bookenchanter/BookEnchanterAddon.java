package de.tore.bookenchanter;

import com.mojang.logging.LogUtils;
import de.tore.bookenchanter.modules.AutoBookEnchant;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class BookEnchanterAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Book Enchanter");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Book Enchanter");

        Modules.get().add(new AutoBookEnchant());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "de.tore.bookenchanter";
    }

    @Override
    public GithubRepo getRepo() {
        // TODO: replace with the real repository once it is published.
        return new GithubRepo("Tore", "book-enchanter");
    }
}

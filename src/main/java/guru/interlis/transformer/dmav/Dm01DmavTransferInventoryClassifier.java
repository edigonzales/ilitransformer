package guru.interlis.transformer.dmav;

import guru.interlis.transformer.model.TransferInventoryClassifier;

import ch.interlis.iom.IomObject;

public final class Dm01DmavTransferInventoryClassifier implements TransferInventoryClassifier {

    public static final String CATEGORY_LFP3 = "dm01-dmav/LFP3";

    @Override
    public void classify(IomObject object, ClassificationSink sink) {
        String tag = object.getobjecttag();
        if (Dm01DmavFixtures.isLfp3RelevantClass(tag)) {
            sink.addTag(CATEGORY_LFP3, tag);
        }
    }
}

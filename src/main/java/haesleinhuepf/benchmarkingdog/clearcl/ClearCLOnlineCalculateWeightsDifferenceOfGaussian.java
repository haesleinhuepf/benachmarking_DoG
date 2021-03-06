package haesleinhuepf.benchmarkingdog.clearcl;

import clearcl.*;
import clearcl.enums.HostAccessType;
import clearcl.enums.ImageChannelDataType;
import clearcl.enums.ImageChannelOrder;
import clearcl.enums.KernelAccessType;
import clearcl.ops.OpsBase;
import haesleinhuepf.benchmarkingdog.StopWatch;

import java.io.IOException;
import java.util.Arrays;

public class ClearCLOnlineCalculateWeightsDifferenceOfGaussian extends OpsBase
{
  ImageCache mMinuendFilterKernelImageCache;
  ImageCache mSubtrahendFilterKernelImageCache;
  ImageCache mOutputImageCache;

  ClearCLContext mContext;
  private ClearCLKernel mSubtractionConvolvedKernelImage2F;

  public ClearCLOnlineCalculateWeightsDifferenceOfGaussian(ClearCLQueue pClearCLQueue) throws
                                                                 IOException
  {
    super(pClearCLQueue);
    mContext = getContext();
    mMinuendFilterKernelImageCache = new ImageCache(mContext);
    mSubtrahendFilterKernelImageCache = new ImageCache(mContext);
    mOutputImageCache = new ImageCache(mContext);

    ClearCLProgram
        lConvolutionProgram =
        getContext().createProgram(ClearCLGaussianBlur.class,
                                   "convolution.cl");

    lConvolutionProgram.addBuildOptionAllMathOpt();
    lConvolutionProgram.addDefine("FLOAT");
    lConvolutionProgram.buildAndLog();

    mSubtractionConvolvedKernelImage2F =
        lConvolutionProgram.createKernel(
            "subtract_convolved_images_2d_fast");
  }

  public ClearCLImage differenceOfGaussian(ClearCLImage pInputImage,
                                           float pMinuendSigma,
                                           float pSubtrahendSigma)
  {
    int
        lRadius =
        (int) Math.ceil(3.0f * Math.max(pMinuendSigma,
                                        pSubtrahendSigma));
    ClearCLImage
        lMinuendFilterKernelImage =
        ClearCLGaussUtilities.createBlur2DFilterKernelImage(
            mMinuendFilterKernelImageCache,
            pMinuendSigma,
            lRadius);
    ClearCLImage
        lSubtrahendFilterKernelImage =
        ClearCLGaussUtilities.createBlur2DFilterKernelImage(
            mSubtrahendFilterKernelImageCache,
            pSubtrahendSigma,
            lRadius);

    ClearCLImage
        output =
        mOutputImageCache.get2DImage(HostAccessType.ReadWrite,
                                     KernelAccessType.ReadWrite,
                                     ImageChannelOrder.R,
                                     ImageChannelDataType.Float,
                                     pInputImage.getWidth(),
                                     pInputImage.getHeight());

    long[] imageDimensions = pInputImage.getDimensions();
    //    long[] workgroupDimensions = new long[imageDimensions.length];
    //    for (int i = 0; i < imageDimensions.length; i++) {
    //      workgroupDimensions[i] = 4;
    //    }

    System.out.println("gl: " + Arrays.toString(imageDimensions));
    //System.out.println("ws: " + Arrays.toString(workgroupDimensions));

    mSubtractionConvolvedKernelImage2F.setArgument("input",
                                                   pInputImage);
    mSubtractionConvolvedKernelImage2F.setArgument("output", output);
    mSubtractionConvolvedKernelImage2F.setArgument("radius", lRadius);
    mSubtractionConvolvedKernelImage2F.setArgument("sigma_minuend", pMinuendSigma);
    mSubtractionConvolvedKernelImage2F.setArgument("sigma_subtrahend", pSubtrahendSigma);
    mSubtractionConvolvedKernelImage2F.setGlobalSizes(imageDimensions);
    //mSubtractionConvolvedKernelImage2F.setLocalSizes(workgroupDimensions);

    long[] sizes = new long[pInputImage.getDimensions().length];
    long[] offsets = new long[pInputImage.getDimensions().length];
    for (int i = 0; i < sizes.length; i++) {
      sizes[i] = pInputImage.getDimensions()[i];
      offsets[i] = 0;
    }

    long originalSize0 = sizes[0];
    long numberOfSplits = sizes[0] * sizes[1] / 65536;

    StopWatch sw = new StopWatch();
    for (int j = 0; j < numberOfSplits; j++) {
      if (j < numberOfSplits - 1) {
        sizes[0] = originalSize0 / numberOfSplits;
      } else {
        sizes[0] = originalSize0 - offsets[0];
      }

      //System.out.println("f offset: " + Arrays.toString(offsets));
      //System.out.println("f sizes: " + Arrays.toString(sizes));
      mSubtractionConvolvedKernelImage2F.setGlobalSizes(sizes);
      mSubtractionConvolvedKernelImage2F.setGlobalOffsets(offsets);
      try
      {
        sw.start();
        mSubtractionConvolvedKernelImage2F.run();
      } catch (Throwable t) {
        sw.stop("Catch after");
        t.printStackTrace();


      }
      offsets[0] += sizes[0];
    }
    return output;
  }
}
/* 
 * Copyright 2016 Lutz Fischer <l.fischer@ed.ac.uk>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rappsilber.ms.spectra.match.matcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeMap;
import rappsilber.ms.ToleranceUnit;
import rappsilber.ms.sequence.ions.Fragment;
import rappsilber.ms.sequence.ions.PeptideIon;
import rappsilber.ms.sequence.ions.loss.CrosslinkerModified;
import rappsilber.ms.spectra.Spectra;
import rappsilber.ms.spectra.SpectraPeakCluster;
import rappsilber.ms.spectra.SpectraPeak;
import rappsilber.ms.spectra.annotation.SpectraPeakAnnotation;
import rappsilber.ms.spectra.annotation.SpectraPeakMatchedFragment;
import rappsilber.ms.spectra.match.MatchedBaseFragment;
import rappsilber.ms.spectra.match.MatchedFragmentCollection;
import rappsilber.utils.Util;

/**
 * Non-fancy matching of possible fragments against the spectra
 * first all detected isotope cluster gets checked, whether there m/z and charge
 * state resuld in a mass - 
 *
 * @author Lutz Fischer <l.fischer@ed.ac.uk>
 */
public class DirectMatchFragmentsTreeFC implements Match{

    private void MFNG_Cluster(Collection<SpectraPeakCluster> clusters, TreeMap<Double, ArrayList<Fragment>> ftree, ToleranceUnit tolerance, MatchedFragmentCollection matchedFragments) {
        // first go through the peaks/peak clusters, match them to the first peptide
        // and store the none matched ones in a new spectra
        // first the cluster
        for (SpectraPeakCluster c : clusters) {
            SpectraPeak m = c.getMonoIsotopic();
//            if (! s.getPeaks().contains(m)) {
//                m.annotate(SpectraPeakAnnotation.virtual);
//                s.addPeak(m);
//                System.out.println("virtual peak");
//            }

            double cmz = c.getMZ();
            int cCharge = c.getCharge();
//            double monoMZ = cmz;


            double missingMZ = cmz - (Util.PROTON_MASS/cCharge);



            double monoNeutral = (cmz - Util.PROTON_MASS) * cCharge;
            double missingNeutral = (missingMZ  - Util.PROTON_MASS) * cCharge;

            Collection<ArrayList<Fragment>> subSet = ftree.subMap(tolerance.getMinRange(monoNeutral), tolerance.getMaxRange(monoNeutral)).values();

            boolean matched = false;
            for (ArrayList<Fragment> af : subSet)
                for (Fragment f : af) {
//                        if (f instanceof CrosslinkerModified) {
//                            System.out.println("found it" + f.name());
//                        }
//                        if (f instanceof CrosslinkerModified.CrosslinkerModifiedRest) {
//                            System.out.println("found it " + f.name());
//                        }
                        m.annotate(new SpectraPeakMatchedFragment(f, cCharge, c));

                        // was the same fragment with the same charge matched previously ?
                        if (matchedFragments.hasMatchedFragment(f, cCharge)) { 
                            // if yes it must have been an missing-peak so we should delete that
                            SpectraPeak prevPeak = matchedFragments.getMatchedPeak(f, cCharge);
                            for (SpectraPeakMatchedFragment mf : prevPeak.getMatchedAnnotation())
                                if (mf.getFragment() == f) {
                                    prevPeak.deleteAnnotation(mf);
                                    break;
                                }
                            matchedFragments.remove(f, cCharge);
                        }
                        matchedFragments.add(f, cCharge, m);
                        matched = true;
                }

            if (!matched) {
                subSet = ftree.subMap(tolerance.getMinRange(missingNeutral), tolerance.getMaxRange(missingNeutral)).values();

                // if something was matched
                for (ArrayList<Fragment> af : subSet)
                    for (Fragment f : af) {
                        if (!matchedFragments.hasMatchedFragment(f, cCharge)) {
                            m.annotate(new SpectraPeakMatchedFragment(f, cCharge, missingMZ, c));
                            matchedFragments.add(f, cCharge, m);
                        }
                    }
            }

        }
    }

    private void MFNG_Peak(Spectra s, TreeMap<Double, ArrayList<Fragment>> ftree, ToleranceUnit tolerance, MatchedFragmentCollection matchedFragments) {
        int maxCharge = s.getPrecurserCharge();
        // next single peaks
        for (SpectraPeak p : s.getPeaks()) {
            if (p.hasAnnotation(SpectraPeakAnnotation.isotop) || p.hasAnnotation(SpectraPeakAnnotation.monoisotop))
                continue;


            double peakMZ = p.getMZ();




            boolean matched = false;

            for (int charge = maxCharge;charge >0 ; charge --) {

                double monoNeutral = (peakMZ - Util.PROTON_MASS) * charge;
                double missingNeutral = monoNeutral  - Util.PROTON_MASS;
                double missingMZ = missingNeutral/charge  + Util.PROTON_MASS;

                Collection<ArrayList<Fragment>> subSet = ftree.subMap(tolerance.getMinRange(monoNeutral), tolerance.getMaxRange(monoNeutral)).values();

                for (ArrayList<Fragment> af : subSet)
                    for (Fragment f : af) {
                            // if it has been mathed somewhere before
                            if (matchedFragments.hasMatchedFragment(f, charge)) {
                                // if yes it must have been an missing-peak so we should delete that
                                SpectraPeak prevPeak = matchedFragments.getMatchedPeak(f, charge);
                                for (SpectraPeakMatchedFragment mf : prevPeak.getMatchedAnnotation())
                                    if (mf.getFragment() == f) {
                                        prevPeak.deleteAnnotation(mf);
                                        break;
                                    }
                                matchedFragments.remove(f, charge);
                            }
                            p.annotate(new SpectraPeakMatchedFragment(f, charge));
//                            if (f instanceof CrosslinkerModified) {
//                                System.out.println("found it" + f.name());
//                            }
//                            if (f instanceof CrosslinkerModified.CrosslinkerModifiedRest) {
//                                System.out.println("found it" + f.name());
//                            }
                            matchedFragments.add(f, charge, p);
                            matched =true;
                    }

            }
            if (!matched) {
                for (int charge = maxCharge;charge >0 ; charge --) {

                    double monoNeutral = (peakMZ - Util.PROTON_MASS) * charge;
                    double missingNeutral = monoNeutral  - Util.PROTON_MASS;
                    double missingMZ = missingNeutral/charge  + Util.PROTON_MASS;

                    Collection<ArrayList<Fragment>> subSet = ftree.subMap(tolerance.getMinRange(missingNeutral), tolerance.getMaxRange(missingNeutral)).values();

                    for (ArrayList<Fragment> af : subSet)
                        for (Fragment f : af) {
                            if (!matchedFragments.hasMatchedFragment(f, charge)) {
                                p.annotate(new SpectraPeakMatchedFragment(f, charge, missingMZ));
                                matchedFragments.add(f, charge, p);
                            }
                        }
                }
            }


        }
    }

    private TreeMap<Double,ArrayList<Fragment>> makeTree(ArrayList<Fragment> frags) {
        TreeMap<Double,ArrayList<Fragment>> tree = new TreeMap<Double, ArrayList<Fragment>>();
        for (Fragment f : frags) {
            double mass = f.getNeutralMass();
            ArrayList fl = tree.get(mass);
            if (fl == null) {
                fl = new ArrayList<Fragment>(1);
                tree.put(mass, fl);
            }
            fl.add(f);
        }
        return tree;

    }

    private TreeMap<Double,ArrayList<Fragment>> makeTreeAllChargeStates(ArrayList<Fragment> frags, int maxCharge) {
        TreeMap<Double,ArrayList<Fragment>> tree = new TreeMap<Double, ArrayList<Fragment>>();
        for (Fragment f : frags) {
            for (int c = maxCharge; c>0; c--) {
                double mass = f.getMass(c);
                ArrayList fl = tree.get(mass);
                if (fl == null) {
                    fl = new ArrayList<Fragment>(1);
                    tree.put(mass, fl);
                }
                fl.add(f);
            }
        }
        return tree;

    }
    
    public Spectra matchFragments(Spectra s, ArrayList<Fragment> frags, ToleranceUnit tolerance, MatchedFragmentCollection matchedFragments) {

        TreeMap<Double,ArrayList<Fragment>> ftree = makeTree(frags);
        TreeMap<Double,ArrayList<Fragment>> ftreeC = makeTreeAllChargeStates(frags, s.getPrecurserCharge());

        Collection<SpectraPeakCluster> clusters = s.getIsotopeClusters();

        Spectra unmatched = s.cloneEmpty();
        // first go through the peaks/peak clusters, match them to the first peptide
        // and store the none matched ones in a new spectra
        // first the cluster
        for (SpectraPeakCluster c : clusters) {
            SpectraPeak m = c.getMonoIsotopic();
//            if (! s.getPeaks().contains(m)) {
//                m.annotate(SpectraPeakAnnotation.virtual);
//                s.addPeak(m);
//                System.out.println("virtual peak");
//            }

            boolean matched = false;
            double cmz = c.getMZ();
            int cCharge = c.getCharge();
//            double monoMZ = cmz;

            double missingMZ = cmz - (Util.PROTON_MASS/cCharge);

            double monoNeutral = (cmz - Util.PROTON_MASS) * cCharge;
            double missingNeutral = (missingMZ  - Util.PROTON_MASS) * cCharge;

            Collection<ArrayList<Fragment>> subSet = ftree.subMap(tolerance.getMinRange(monoNeutral), tolerance.getMaxRange(monoNeutral)).values();

            for (ArrayList<Fragment> af : subSet)
                for (Fragment f : af) {
                        m.annotate(new SpectraPeakMatchedFragment(f, cCharge, c));
                        matchedFragments.add(f, cCharge, m);
                        matched = true;
                }

            subSet = ftree.subMap(tolerance.getMinRange(missingNeutral), tolerance.getMaxRange(missingNeutral)).values();

            for (ArrayList<Fragment> af : subSet)
                for (Fragment f : af) {
                        m.annotate(new SpectraPeakMatchedFragment(f, cCharge, missingMZ, c));
                        matchedFragments.add(f, cCharge, m);
                        matched = true;
                }

            if (!matched) {
                unmatched.getIsotopeClusters().add(c);
                unmatched.addPeak(c.getMonoIsotopic());
            }

        }

        int maxCharge = s.getPrecurserCharge();
        // next single peaks
        for (SpectraPeak p : s.getPeaks()) {
            if (p.hasAnnotation(SpectraPeakAnnotation.isotop) || p.hasAnnotation(SpectraPeakAnnotation.monoisotop))
                continue;

            boolean matched = false;
            double peakMZ = p.getMZ();

            for (int charge = maxCharge;charge >0 ; charge --) {

                double monoNeutral = (peakMZ - Util.PROTON_MASS) * charge;
                double missingNeutral = monoNeutral  - Util.PROTON_MASS;
                double missingMZ = missingNeutral/charge  + Util.PROTON_MASS;

                Collection<ArrayList<Fragment>> subSet = ftree.subMap(tolerance.getMinRange(monoNeutral), tolerance.getMaxRange(monoNeutral)).values();

                for (ArrayList<Fragment> af : subSet)
                    for (Fragment f : af) {
                            p.annotate(new SpectraPeakMatchedFragment(f, charge));
                            matchedFragments.add(f, charge, p);
                            matched = true;
                    }

                subSet = ftree.subMap(tolerance.getMinRange(missingNeutral), tolerance.getMaxRange(missingNeutral)).values();

                for (ArrayList<Fragment> af : subSet)
                    for (Fragment f : af) {
                            p.annotate(new SpectraPeakMatchedFragment(f, charge, missingMZ));
                            matchedFragments.add(f, charge, p);
                            matched = true;
                    }
            }

            if (!matched) {
                //unmatched.getIsotopClusters().add(c);
                unmatched.addPeak(p);
            }
        }
        
        for (ArrayList<Fragment> v: ftree.values()) {
            v.clear();
        }
        ftree.clear();

        return unmatched;
    }



    public void matchFragmentsNonGreedy(Spectra s, ArrayList<Fragment> frags, ToleranceUnit tolerance, MatchedFragmentCollection matchedFragments) {

        TreeMap<Double,ArrayList<Fragment>> ftree = makeTree(frags);
        TreeMap<Double,ArrayList<Fragment>> ftreeC = makeTreeAllChargeStates(frags, s.getPrecurserCharge());

        Collection<SpectraPeakCluster> clusters = s.getIsotopeClusters();
        MFNG_Cluster(clusters, ftree, tolerance, matchedFragments);
        MFNG_Peak(s, ftreeC, tolerance, matchedFragments);

        for (ArrayList<Fragment> v: ftree.values()) {
            v.clear();
        }
        ftree.clear();

    }

}

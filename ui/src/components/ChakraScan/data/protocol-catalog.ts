import { ProtocolCatalogEntry } from "../types/spatial-chakra";

export const PROTOCOL_CATALOG: ProtocolCatalogEntry[] = [
  // ─── Basic ───
  { id: "basic-headache", name: "Headache Relief", category: "General Conditions", level: "basic" },
  { id: "basic-fever", name: "Fever Management", category: "General Conditions", level: "basic" },
  { id: "basic-cold", name: "Common Cold", category: "General Conditions", level: "basic" },
  { id: "basic-cough", name: "Cough Protocol", category: "General Conditions", level: "basic" },
  { id: "basic-eye-strain", name: "Eye Strain", category: "General Conditions", level: "basic" },
  { id: "basic-stress", name: "Stress Relief", category: "General Conditions", level: "basic" },
  { id: "basic-wound", name: "Simple Wound Healing", category: "General Conditions", level: "basic" },
  { id: "basic-toothache", name: "Toothache", category: "General Conditions", level: "basic" },
  { id: "basic-burns", name: "Minor Burns", category: "General Conditions", level: "basic" },
  { id: "basic-back-pain", name: "Back Pain (Simple)", category: "Pain Management", level: "basic" },
  { id: "basic-neck-pain", name: "Neck Pain", category: "Pain Management", level: "basic" },
  { id: "basic-knee-pain", name: "Knee Pain", category: "Pain Management", level: "basic" },
  { id: "basic-shoulder-pain", name: "Shoulder Pain", category: "Pain Management", level: "basic" },
  { id: "basic-muscle-cramps", name: "Muscle Cramps", category: "Pain Management", level: "basic" },
  { id: "basic-body-pain", name: "General Body Pain", category: "Pain Management", level: "basic" },

  // ─── Advanced ───
  { id: "adv-asthma", name: "Asthma Protocol", category: "Respiratory", level: "advanced" },
  { id: "adv-bronchitis", name: "Bronchitis Protocol", category: "Respiratory", level: "advanced" },
  { id: "adv-sinusitis", name: "Sinusitis Protocol", category: "Respiratory", level: "advanced" },
  { id: "adv-pneumonia", name: "Pneumonia Support", category: "Respiratory", level: "advanced" },
  { id: "adv-copd", name: "COPD Support", category: "Respiratory", level: "advanced" },
  { id: "adv-hypertension", name: "Hypertension Protocol", category: "Cardiovascular", level: "advanced" },
  { id: "adv-heart", name: "Heart Strengthening", category: "Cardiovascular", level: "advanced" },
  { id: "adv-cholesterol", name: "Cholesterol Management", category: "Cardiovascular", level: "advanced" },
  { id: "adv-circulation", name: "Circulation Improvement", category: "Cardiovascular", level: "advanced" },
  { id: "adv-gastritis", name: "Gastritis/Ulcer Protocol", category: "Gastrointestinal", level: "advanced" },
  { id: "adv-liver", name: "Liver Cleansing", category: "Gastrointestinal", level: "advanced" },
  { id: "adv-constipation", name: "Constipation Protocol", category: "Gastrointestinal", level: "advanced" },
  { id: "adv-ibs", name: "IBS Support", category: "Gastrointestinal", level: "advanced" },
  { id: "adv-gerd", name: "GERD Protocol", category: "Gastrointestinal", level: "advanced" },
  { id: "adv-arthritis", name: "Arthritis Protocol", category: "Musculoskeletal", level: "advanced" },
  { id: "adv-spondylosis", name: "Spondylosis Protocol", category: "Musculoskeletal", level: "advanced" },
  { id: "adv-disc", name: "Herniated Disc Support", category: "Musculoskeletal", level: "advanced" },
  { id: "adv-frozen-shoulder", name: "Frozen Shoulder", category: "Musculoskeletal", level: "advanced" },
  { id: "adv-sciatica", name: "Sciatica Protocol", category: "Musculoskeletal", level: "advanced" },
  { id: "adv-diabetes", name: "Diabetes Protocol", category: "Endocrine", level: "advanced" },
  { id: "adv-thyroid", name: "Thyroid Balancing", category: "Endocrine", level: "advanced" },
  { id: "adv-hormonal", name: "Hormonal Balance", category: "Endocrine", level: "advanced" },
  { id: "adv-adrenal", name: "Adrenal Support", category: "Endocrine", level: "advanced" },
  { id: "adv-migraine", name: "Migraine Protocol", category: "Neurological", level: "advanced" },
  { id: "adv-epilepsy", name: "Epilepsy Support", category: "Neurological", level: "advanced" },
  { id: "adv-neuropathy", name: "Neuropathy Protocol", category: "Neurological", level: "advanced" },
  { id: "adv-vertigo", name: "Vertigo Protocol", category: "Neurological", level: "advanced" },
  { id: "adv-bells-palsy", name: "Bell's Palsy Support", category: "Neurological", level: "advanced" },
  { id: "adv-cancer-general", name: "General Cancer Support", category: "Cancer Support", level: "advanced" },
  { id: "adv-cancer-tumor", name: "Tumor Reduction Support", category: "Cancer Support", level: "advanced" },
  { id: "adv-cancer-chemo", name: "Chemotherapy Support", category: "Cancer Support", level: "advanced" },
  { id: "adv-cancer-post-surgery", name: "Post-Surgery Recovery", category: "Cancer Support", level: "advanced" },
  { id: "adv-cancer-immune", name: "Immune Boost", category: "Cancer Support", level: "advanced" },
  { id: "adv-menstrual", name: "Menstrual Disorders", category: "Reproductive", level: "advanced" },
  { id: "adv-pcos", name: "PCOS Support", category: "Reproductive", level: "advanced" },
  { id: "adv-infertility-f", name: "Infertility Support (F)", category: "Reproductive", level: "advanced" },
  { id: "adv-infertility-m", name: "Infertility Support (M)", category: "Reproductive", level: "advanced" },
  { id: "adv-menopausal", name: "Menopausal Support", category: "Reproductive", level: "advanced" },
  { id: "adv-prostate", name: "Prostate Protocol", category: "Reproductive", level: "advanced" },
  { id: "adv-eye-general", name: "Eye Healing General", category: "Eye / ENT", level: "advanced" },
  { id: "adv-glaucoma", name: "Glaucoma Support", category: "Eye / ENT", level: "advanced" },
  { id: "adv-cataract", name: "Cataract Support", category: "Eye / ENT", level: "advanced" },
  { id: "adv-tinnitus", name: "Tinnitus Protocol", category: "Eye / ENT", level: "advanced" },
  { id: "adv-hearing", name: "Hearing Enhancement", category: "Eye / ENT", level: "advanced" },
  { id: "adv-eczema", name: "Eczema Protocol", category: "Skin", level: "advanced" },
  { id: "adv-psoriasis", name: "Psoriasis Protocol", category: "Skin", level: "advanced" },
  { id: "adv-acne", name: "Acne Treatment", category: "Skin", level: "advanced" },
  { id: "adv-wound-complex", name: "Wound Healing (Complex)", category: "Skin", level: "advanced" },
  { id: "adv-allergy-hives", name: "Allergy/Hives", category: "Skin", level: "advanced" },
  { id: "adv-anemia", name: "Anemia Protocol", category: "Blood / Immune", level: "advanced" },
  { id: "adv-immune-boost", name: "Immune System Boost", category: "Blood / Immune", level: "advanced" },
  { id: "adv-allergy-protocol", name: "Allergy Protocol", category: "Blood / Immune", level: "advanced" },
  { id: "adv-autoimmune", name: "Autoimmune Support", category: "Blood / Immune", level: "advanced" },
  { id: "adv-blood-purify", name: "Blood Purification", category: "Blood / Immune", level: "advanced" },

  // ─── Psychotherapy ───
  { id: "psy-stress-deep", name: "Stress Relief (Deep)", category: "Stress & Anxiety", level: "psychotherapy" },
  { id: "psy-anxiety", name: "Anxiety Protocol", category: "Stress & Anxiety", level: "psychotherapy" },
  { id: "psy-panic", name: "Panic Attack Support", category: "Stress & Anxiety", level: "psychotherapy" },
  { id: "psy-phobia", name: "Phobia Release", category: "Stress & Anxiety", level: "psychotherapy" },
  { id: "psy-fear", name: "Fear Dissolution", category: "Stress & Anxiety", level: "psychotherapy" },
  { id: "psy-depression", name: "Depression Protocol", category: "Depression & Mood", level: "psychotherapy" },
  { id: "psy-grief", name: "Grief Healing", category: "Depression & Mood", level: "psychotherapy" },
  { id: "psy-trauma-emotional", name: "Emotional Trauma Release", category: "Depression & Mood", level: "psychotherapy" },
  { id: "psy-mood", name: "Mood Stabilization", category: "Depression & Mood", level: "psychotherapy" },
  { id: "psy-self-esteem", name: "Self-Esteem Enhancement", category: "Depression & Mood", level: "psychotherapy" },
  { id: "psy-addiction", name: "Addiction Release", category: "Behavioral", level: "psychotherapy" },
  { id: "psy-ocd", name: "OCD Support", category: "Behavioral", level: "psychotherapy" },
  { id: "psy-compulsive", name: "Compulsive Behavior", category: "Behavioral", level: "psychotherapy" },
  { id: "psy-anger", name: "Anger Management", category: "Behavioral", level: "psychotherapy" },
  { id: "psy-relationship", name: "Relationship Healing", category: "Behavioral", level: "psychotherapy" },
  { id: "psy-ptsd", name: "PTSD Protocol", category: "Trauma", level: "psychotherapy" },
  { id: "psy-childhood-trauma", name: "Childhood Trauma Release", category: "Trauma", level: "psychotherapy" },
  { id: "psy-entity", name: "Entity/Thought Form Removal", category: "Trauma", level: "psychotherapy" },
  { id: "psy-psychic-attack", name: "Psychic Attack Protection", category: "Trauma", level: "psychotherapy" },

  // ─── Crystal ───
  { id: "crystal-cleansing", name: "Crystal-Assisted Cleansing", category: "Crystal Healing", level: "crystal" },
  { id: "crystal-energizing", name: "Crystal Energizing Protocol", category: "Crystal Healing", level: "crystal" },
  { id: "crystal-meditation", name: "Crystal Meditation", category: "Crystal Healing", level: "crystal" },
  { id: "crystal-layout", name: "Chakra Crystal Layout", category: "Crystal Healing", level: "crystal" },
];

export const PROTOCOL_LEVELS = ["basic", "advanced", "psychotherapy", "crystal"] as const;

export const PROTOCOL_LEVEL_LABELS: Record<string, string> = {
  basic: "Basic",
  advanced: "Advanced",
  psychotherapy: "Psychotherapy",
  crystal: "Crystal",
};

export function searchProtocols(query: string): ProtocolCatalogEntry[] {
  const q = query.toLowerCase().trim();
  if (!q) return PROTOCOL_CATALOG;
  return PROTOCOL_CATALOG.filter(
    (p) =>
      p.name.toLowerCase().includes(q) ||
      p.category.toLowerCase().includes(q) ||
      p.level.toLowerCase().includes(q)
  );
}

export function getProtocolsByLevel(level: string): ProtocolCatalogEntry[] {
  return PROTOCOL_CATALOG.filter((p) => p.level === level);
}

export function groupProtocolsByLevel(): Record<string, ProtocolCatalogEntry[]> {
  const grouped: Record<string, ProtocolCatalogEntry[]> = {};
  for (const p of PROTOCOL_CATALOG) {
    if (!grouped[p.level]) grouped[p.level] = [];
    grouped[p.level].push(p);
  }
  return grouped;
}

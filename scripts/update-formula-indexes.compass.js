use("mathAssistant");

const formulas = db.getCollection("formulas");
const existingIndexes = formulas.getIndexes();

if (existingIndexes.some((index) => index.name === "unique_name_group")) {
  formulas.dropIndex("unique_name_group");
  print("Dropped old index: unique_name_group");
} else {
  print("Old index not found: unique_name_group");
}

formulas.createIndex(
  { name: 1, group: 1, status: 1, ownerUserId: 1 },
  { name: "unique_formula_scope", unique: true }
);

print("Ensured index: unique_formula_scope");
printjson(formulas.getIndexes());

use("mathAssistant");

const formulas = db.getCollection("formulas");
const ratings = db.getCollection("formula_ratings");
const comments = db.getCollection("formula_comments");
const existingIndexes = formulas.getIndexes();
const existingCommentIndexes = comments.getIndexes();

if (existingIndexes.some((index) => index.name === "unique_name_group")) {
  formulas.dropIndex("unique_name_group");
  print("Dropped old index: unique_name_group");
} else {
  print("Old index not found: unique_name_group");
}

if (
  existingCommentIndexes.some(
    (index) => index.name === "formula_comment_listing",
  )
) {
  comments.dropIndex("formula_comment_listing");
  print("Dropped old index: formula_comment_listing");
} else {
  print("Old comment index not found: formula_comment_listing");
}

formulas.createIndex(
  { name: 1, group: 1, status: 1, ownerUserId: 1 },
  { name: "unique_formula_scope", unique: true },
);

formulas.createIndex(
  { status: 1, group: 1, variable: 1, name: 1 },
  { name: "formula_public_search" },
);

formulas.createIndex(
  { ownerUserId: 1, status: 1, group: 1, variable: 1, name: 1 },
  { name: "formula_owner_search" },
);

formulas.createIndex(
  { status: 1, upvotes: -1, averageRating: -1, createdAt: -1 },
  { name: "formula_social_search" },
);

ratings.createIndex(
  { formulaId: 1, userId: 1 },
  { name: "unique_formula_rating", unique: true },
);

comments.createIndex(
  { formulaId: 1, parentCommentId: 1, deleted: 1, createdAt: -1 },
  { name: "formula_comment_thread" },
);

print("Ensured index: unique_formula_scope");
print("Ensured index: formula_public_search");
print("Ensured index: formula_owner_search");
print("Ensured index: formula_social_search");
print("Ensured index: unique_formula_rating");
print("Ensured index: formula_comment_thread");
printjson(formulas.getIndexes());
printjson(ratings.getIndexes());
printjson(comments.getIndexes());

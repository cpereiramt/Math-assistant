param(
  [string]$ContainerName = "mongodb",
  [string]$Database = "mathAssistant",
  [string]$Username = "admin",
  [string]$Password = "admin123",
  [string]$AuthDatabase = "admin"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw "Docker CLI was not found. Start MongoDB manually and run the equivalent mongosh commands."
}

$runningContainer = docker ps --format "{{.Names}}" | Where-Object { $_ -eq $ContainerName }
if (-not $runningContainer) {
  throw "MongoDB container '$ContainerName' is not running. Start it with: docker-compose up -d"
}

$indexScript = @'
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
'@

docker exec $ContainerName mongosh `
  --quiet `
  --username $Username `
  --password $Password `
  --authenticationDatabase $AuthDatabase `
  $Database `
  --eval $indexScript

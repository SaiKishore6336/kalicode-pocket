"""KaliCode Pocket playground.

Edit this file on your phone. Python syntax highlighting, smart indentation,
and bracket pairing are built in. Use the Run button for options.
"""

from dataclasses import dataclass


@dataclass
class Project:
    name: str
    files: int
    language: str

    def summary(self) -> str:
        return f"{self.name}: {self.files} files · {self.language}"


def main() -> None:
    projects = [
        Project("welcome", 4, "Python"),
        Project("mobile-ide", 12, "Kotlin"),
        Project("landing-page", 3, "HTML"),
    ]

    print("KaliCode Pocket")
    for project in projects:
        print(" •", project.summary())

    print("\nEdit this script, then preview the HTML sample.")


if __name__ == "__main__":
    main()

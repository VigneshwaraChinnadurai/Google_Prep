"""
Database connection and session management.
"""
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.ext.declarative import declarative_base
from contextlib import contextmanager
from typing import Generator
import logging

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()

# Create database engine
engine = create_engine(
    settings.database_url,
    pool_pre_ping=True,
    pool_size=10,
    max_overflow=20,
    echo=settings.debug
)

# Create session factory
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Base class for models
Base = declarative_base()


def get_db() -> Generator[Session, None, None]:
    """
    Dependency for FastAPI endpoints to get database session.
    Automatically closes session when done.
    """
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


@contextmanager
def get_db_context() -> Generator[Session, None, None]:
    """
    Context manager for database sessions.
    Use this in non-FastAPI contexts (agents, schedulers).
    """
    db = SessionLocal()
    try:
        yield db
        db.commit()
    except Exception as e:
        db.rollback()
        logger.error(f"Database error: {e}")
        raise
    finally:
        db.close()


def init_db():
    """Initialize database tables."""
    from database.models import Base
    Base.metadata.create_all(bind=engine)
    logger.info("Database tables created successfully")


def drop_db():
    """Drop all database tables. Use with caution!"""
    from database.models import Base
    Base.metadata.drop_all(bind=engine)
    logger.warning("All database tables dropped")
